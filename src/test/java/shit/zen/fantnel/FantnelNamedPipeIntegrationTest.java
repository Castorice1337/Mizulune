package shit.zen.fantnel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in Windows smoke test against the staged, self-contained FantNEL Host. */
@EnabledOnOs(OS.WINDOWS)
final class FantnelNamedPipeIntegrationTest {
    @Test
    void consecutiveRequestsUseOneSynchronousPipeWithoutDeadlocking() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mizulune.fantnel.realPipeTest"));
        Path host = Path.of(System.getProperty("user.dir"),
            "build", "mod-dist", "fabric", "fantnel", "Mizulune.FantnelHost.exe")
            .toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(host), "staged FantNEL Host is missing");

        String previousOverride = System.getProperty("mizulune.fantnel.host");
        System.setProperty("mizulune.fantnel.host", host.toString());
        FantnelHostClient client = FantnelHostClient.getInstance();
        try {
            JsonObject firstStatus = client.request("host.status")
                .get(100, TimeUnit.SECONDS).getAsJsonObject();
            JsonElement accounts = client.request("account.list")
                .get(100, TimeUnit.SECONDS);
            JsonObject secondStatus = client.request("host.status")
                .get(100, TimeUnit.SECONDS).getAsJsonObject();

            assertTrue(firstStatus.has("initialized"));
            assertTrue(accounts.isJsonArray());
            assertTrue(secondStatus.has("initialized"));
        } finally {
            client.close();
            if (previousOverride == null) System.clearProperty("mizulune.fantnel.host");
            else System.setProperty("mizulune.fantnel.host", previousOverride);
        }
    }
}
