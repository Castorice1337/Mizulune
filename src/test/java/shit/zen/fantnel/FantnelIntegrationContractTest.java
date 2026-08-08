package shit.zen.fantnel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the Java in-game client to the frozen C# FantNEL host contract. */
final class FantnelIntegrationContractTest {
    @Test
    void javaWhitelistExactlyMatchesTheHostDispatcher() throws Exception {
        String dispatcher = source("backends/Mizulune.FantnelHost/FantnelDispatcher.cs");
        Matcher matcher = Pattern.compile("case \\\"([^\\\"]+)\\\"").matcher(dispatcher);
        Set<String> methods = new LinkedHashSet<>();
        while (matcher.find()) methods.add(matcher.group(1));

        assertEquals(methods, FantnelHostClient.allowedMethodsForTesting());
    }

    @Test
    void typedClientEmitsTheFrozenProxyAndAccountShapes() {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<JsonObject> params = new AtomicReference<>();
        FantnelClientService service = new FantnelClientService((name, value) -> {
            method.set(name);
            params.set(value.deepCopy());
            if (name.equals("proxy.start")) {
                JsonObject proxy = new JsonObject();
                proxy.addProperty("id", 7);
                proxy.addProperty("localAddress", "127.0.0.1");
                proxy.addProperty("localPort", 25577);
                proxy.addProperty("endpoint", "127.0.0.1:25577");
                proxy.addProperty("serverName", "masked-server");
                proxy.addProperty("roleName", "masked-role");
                return CompletableFuture.completedFuture(proxy);
            }
            return CompletableFuture.completedFuture(new JsonObject());
        });

        service.saveAccount("4399", "masked-account", "credential-secret").join();
        assertEquals("account.save", method.get());
        assertEquals(Set.of("type", "account", "credential"), params.get().keySet());
        assertEquals("credential-secret", params.get().get("credential").getAsString());

        service.loginCredentials("4399", "masked-account", "credential-secret").join();
        assertEquals("account.login.credentials", method.get());
        assertEquals(Set.of("type", "account", "credential"), params.get().keySet());
        assertEquals("masked-account", params.get().get("account").getAsString());

        FantnelClientService.Proxy proxy = service.startProxy("game-id", "masked-role", 25577).join();
        assertEquals("proxy.start", method.get());
        assertEquals("net", params.get().get("mode").getAsString());
        assertEquals(25577, params.get().get("localPort").getAsInt());
        assertEquals("127.0.0.1:25577", proxy.endpoint());
    }

    @Test
    void sensitiveRequestValuesAreRemovedFromHostErrors() {
        String secret = "credential-secret-value";
        String redacted = FantnelHostClient.redactSensitive(
            "login failed for credential=" + secret + "\nretry denied",
            Set.of(secret));
        assertFalse(redacted.contains(secret));
        assertFalse(redacted.contains("\n"));
        assertTrue(redacted.contains("[redacted]"));
    }

    @Test
    void onlyTheExactLocalProxyConnectionOwnsTheSession() {
        FantnelClientService.Proxy proxy = new FantnelClientService.Proxy(
            7, "127.0.0.1", 25577, "127.0.0.1:25577", "server", "role");
        assertTrue(FantnelProxySession.isProxyAddress(proxy,
            new InetSocketAddress("127.0.0.1", 25577)));
        assertFalse(FantnelProxySession.isProxyAddress(proxy,
            new InetSocketAddress("127.0.0.1", 25578)));
        assertFalse(FantnelProxySession.isProxyAddress(proxy,
            new InetSocketAddress("192.0.2.1", 25577)));
    }

    @Test
    void fabricDisconnectPathsAndUiUseTheSessionOwner() throws Exception {
        String entry = source("fabricmod/src/main/java/shit/zen/fabric/MizuluneFabricClient.java");
        String connection = source("fabricmod/src/main/java/shit/zen/fabric/mixin/ConnectionMixin.java");
        String screen = source("src/main/java/shit/zen/fantnel/ui/FantnelScreen.java");
        String host = source("src/main/java/shit/zen/fantnel/FantnelHostClient.java");

        assertTrue(entry.contains("ClientPlayConnectionEvents.DISCONNECT"));
        assertTrue(connection.contains("FantnelProxySession.onConnectionClosed"));
        assertTrue(screen.contains("FantnelProxySession.activate(proxy)"));
        assertTrue(screen.contains("FantnelProxySession::stopActive"));
        assertTrue(screen.contains("Component.literal(\"登录\")"));
        assertTrue(screen.contains("button -> loginEnteredAccount()"));
        assertTrue(screen.contains("service.loginCredentials(type, account, secret)"));
        assertFalse(screen.contains("Component.literal(\"保存账号\")"));
        assertTrue(screen.contains("captchaPrepared"));
        assertTrue(host.contains("ProcessBuilder.Redirect.DISCARD"));
        assertFalse(host.contains("fantnel-mod-host.log"));
    }

    @Test
    void javaNamedPipeHandshakeNeverReceivesAServerFirstWrite() throws Exception {
        String program = source("backends/Mizulune.FantnelHost/Program.cs");
        String dispatcher = source("backends/Mizulune.FantnelHost/FantnelDispatcher.cs");
        String screen = source("src/main/java/shit/zen/fantnel/ui/FantnelScreen.java");
        String client = source("src/main/java/shit/zen/fantnel/FantnelHostClient.java");

        assertFalse(program.contains("SendEventAsync(\"host.starting\""));
        assertTrue(program.contains("PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly"));
        assertTrue(program.contains("new FantnelInitializationState()"));
        assertTrue(dispatcher.contains("initializing = initialization.IsInitializing"));
        assertTrue(dispatcher.contains("await initialization.EnsureReadyAsync()"));
        assertTrue(screen.contains("scheduleStartupRetry()"));
        assertFalse(screen.contains("initialRequestStarted = false;"));
        assertTrue(client.contains("Executors.newSingleThreadExecutor"));
        int requestWrite = client.indexOf("writeLine(request.toString());");
        int responseRead = client.indexOf("readUntilResponse(", requestWrite);
        assertTrue(requestWrite >= 0 && responseRead > requestWrite);
        assertFalse(client.contains("ioExecutor.execute(() -> readLoop"));
        assertFalse(client.contains("private void readLoop("));
    }

    @Test
    void packagedHostWinsOverThePersistentCache(@TempDir Path directory) throws Exception {
        Path gameDirectory = directory.resolve("game");
        Path processDirectory = directory.resolve("process");
        Path packaged = gameDirectory.resolve("fantnel/Mizulune.FantnelHost.exe");
        Path cached = directory.resolve("profile/backends/fantnel/Mizulune.FantnelHost.exe");
        Files.createDirectories(packaged.getParent());
        Files.createDirectories(cached.getParent());
        Files.writeString(packaged, "current-package", StandardCharsets.UTF_8);
        Files.writeString(cached, "stale-cache", StandardCharsets.UTF_8);

        assertEquals(packaged.toAbsolutePath().normalize(),
            FantnelHostClient.findPreferredHost(gameDirectory, processDirectory, cached).orElseThrow());

        Files.delete(packaged);
        assertEquals(cached.toAbsolutePath().normalize(),
            FantnelHostClient.findPreferredHost(gameDirectory, processDirectory, cached).orElseThrow());
    }

    @Test
    void fabricSidecarUsesTheWindowsGuiSubsystem() throws Exception {
        String project = source("backends/Mizulune.FantnelHost/Mizulune.FantnelHost.csproj");
        String console = source("backends/Mizulune.FantnelHost/HiddenConsoleSession.cs");

        assertTrue(project.contains("<OutputType>WinExe</OutputType>"));
        assertTrue(console.contains("ShowWindow(consoleWindow, SwHide)"));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath),
            StandardCharsets.UTF_8);
    }
}
