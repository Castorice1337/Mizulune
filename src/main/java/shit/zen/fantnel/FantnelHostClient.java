package shit.zen.fantnel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lazy, process-owning client for {@code Mizulune.FantnelHost.exe}.
 *
 * <p>The wire contract is the same one used by the native Loader: one
 * current-user Windows named pipe carrying newline-delimited JSON.  Secrets
 * are never logged and responses are bounded before parsing.</p>
 */
public final class FantnelHostClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mizulune.Fantnel");
    private static final int MAX_LINE_BYTES = 4 * 1024 * 1024;
    private static final Duration START_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration LONG_REQUEST_TIMEOUT = Duration.ofMinutes(10);
    private static final Set<String> ALLOWED_METHODS = Set.of(
        "host.status", "host.shutdown",
        "account.list", "account.available", "account.captcha.begin",
        "account.captcha.submit", "account.captcha.auto", "account.save",
        "account.login.credentials", "account.update", "account.login", "account.switch", "account.delete",
        "server.list", "server.detail", "server.roles", "server.role.create",
        "session.prepare", "launch.start", "launch.list", "launch.stop",
        "proxy.start", "proxy.list", "proxy.stop"
    );
    private static final Set<String> SENSITIVE_PARAMETER_NAMES = Set.of(
        "account", "captcha", "credential", "name", "password", "roleName",
        "session", "token", "userId"
    );
    private static final FantnelHostClient INSTANCE = new FantnelHostClient();

    private final Object lifecycleLock = new Object();
    private final Object writeLock = new Object();
    private final AtomicLong nextId = new AtomicLong();
    private final Map<String, PendingRequest> pending = new ConcurrentHashMap<>();
    private final List<Consumer<HostEvent>> listeners = new CopyOnWriteArrayList<>();
    // RandomAccessFile opens a synchronous Windows named-pipe handle. A native
    // read left pending on that handle prevents a concurrent native write from
    // completing, so requests must stay strictly write-then-read and serialized.
    private final ExecutorService ioExecutor =
        Executors.newSingleThreadExecutor(daemonFactory("Mizulune-Fantnel-IO"));
    private final ScheduledExecutorService timeoutExecutor =
        Executors.newSingleThreadScheduledExecutor(daemonFactory("Mizulune-Fantnel-Timeout"));

    private volatile Path gameDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    private volatile Process process;
    private volatile RandomAccessFile pipe;
    private volatile boolean closing;

    private FantnelHostClient() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "Mizulune-Fantnel-Shutdown"));
    }

    public static FantnelHostClient getInstance() {
        return INSTANCE;
    }

    public void setGameDirectory(Path directory) {
        if (directory != null) gameDirectory = directory.toAbsolutePath().normalize();
    }

    public boolean isRunning() {
        RandomAccessFile currentPipe = pipe;
        Process currentProcess = process;
        return currentPipe != null && currentProcess != null && currentProcess.isAlive();
    }

    public AutoCloseable addEventListener(Consumer<HostEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    public CompletableFuture<JsonElement> request(String method) {
        return request(method, new JsonObject());
    }

    public CompletableFuture<JsonElement> request(String method, JsonObject parameters) {
        if (!ALLOWED_METHODS.contains(method)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Fantnel method is not allowed: " + method));
        }
        JsonObject safeParameters = parameters == null ? new JsonObject() : parameters.deepCopy();
        Duration timeout = method.equals("launch.start") || method.equals("proxy.start")
            ? LONG_REQUEST_TIMEOUT : DEFAULT_REQUEST_TIMEOUT;

        String id = "mc-" + nextId.incrementAndGet();
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(
            () -> timeoutRequest(id, method), timeout.toMillis(), TimeUnit.MILLISECONDS);
        pending.put(id, new PendingRequest(future, timeoutTask, sensitiveValues(safeParameters)));
        ioExecutor.execute(() -> executeRequest(id, method, safeParameters, future));
        return future;
    }

    private void executeRequest(String id, String method, JsonObject parameters,
                                CompletableFuture<JsonElement> future) {
        if (future.isDone()) return;
        Process expectedProcess = null;
        RandomAccessFile expectedPipe = null;
        try {
            ensureStarted();
            if (future.isDone()) return;
            expectedProcess = process;
            expectedPipe = pipe;

            JsonObject request = new JsonObject();
            request.addProperty("id", id);
            request.addProperty("method", method);
            request.add("params", parameters);
            writeLine(request.toString());
            readUntilResponse(expectedProcess, expectedPipe, future);
        } catch (Throwable error) {
            if (!future.isDone()) handleDisconnect(expectedProcess, expectedPipe, error);
        }
    }

    private void ensureStarted() {
        if (isRunning()) return;
        synchronized (lifecycleLock) {
            if (isRunning()) return;
            if (!isWindows()) throw new FantnelHostException("Fantnel Host currently requires Windows x86_64.");
            closing = false;
            cleanupHandles(true);

            HostLaunch launch = prepareLaunch();
            String pipeName = "MizuluneFantnel-" + ProcessHandle.current().pid() + "-"
                + UUID.randomUUID().toString().replace("-", "");
            Path home = Path.of(System.getProperty("user.home"));
            Path stateDirectory = home.resolve(".mizulune/backends/fantnel");
            Path protocolDirectory = home.resolve(".mizulune");
            Path logDirectory = protocolDirectory.resolve("logs");
            try {
                Files.createDirectories(stateDirectory);
                Files.createDirectories(protocolDirectory);
                Files.createDirectories(logDirectory);

                ProcessBuilder builder = new ProcessBuilder(
                    launch.executable().toString(),
                    "--pipe", pipeName,
                    "--parent-pid", Long.toString(ProcessHandle.current().pid()),
                    "--state-dir", stateDirectory.toString(),
                    "--protocol-dir", protocolDirectory.toString()
                );
                builder.directory(launch.workingDirectory().toFile());
                // The sidecar owns a separate, redacted bootstrap log.  Frozen
                // FantNEL stdout is intentionally discarded because it is not
                // part of the JSON contract and may contain account details.
                builder.redirectErrorStream(true);
                builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                process = builder.start();
            } catch (IOException error) {
                throw new FantnelHostException("Unable to start Mizulune.FantnelHost.exe.", error);
            }

            Process started = process;
            long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
            IOException lastError = null;
            while (System.nanoTime() < deadline) {
                if (!started.isAlive()) {
                    throw new FantnelHostException("Fantnel Host exited before creating its control pipe.");
                }
                try {
                    pipe = new RandomAccessFile("\\\\.\\pipe\\" + pipeName, "rw");
                    break;
                } catch (IOException error) {
                    lastError = error;
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new FantnelHostException("Interrupted while waiting for Fantnel Host.", interrupted);
                    }
                }
            }
            if (pipe == null) {
                started.destroyForcibly();
                throw new FantnelHostException("Timed out waiting for the Fantnel control pipe.", lastError);
            }

            started.onExit().thenRun(() -> {
                if (!closing) handleDisconnect(started, null,
                    new FantnelHostException("Fantnel Host stopped unexpectedly."));
            });
        }
    }

    private HostLaunch prepareLaunch() {
        String override = firstNonBlank(System.getProperty("mizulune.fantnel.host"),
            System.getenv("MIZULUNE_FANTNEL_HOST_PATH"));
        if (override != null) {
            Path executable = Path.of(override).toAbsolutePath().normalize();
            requireExecutable(executable);
            return new HostLaunch(executable, executable.getParent());
        }

        Path stateDirectory = Path.of(System.getProperty("user.home"), ".mizulune", "backends", "fantnel")
            .toAbsolutePath().normalize();
        Path cached = stateDirectory.resolve("Mizulune.FantnelHost.exe");
        Path processDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path packaged = findPreferredHost(gameDirectory, processDirectory, cached)
            .orElseThrow(() -> new FantnelHostException(
                "Mizulune.FantnelHost.exe was not found. Set MIZULUNE_FANTNEL_HOST_PATH or install the staged fantnel directory."));

        Path sourceDirectory = packaged.getParent().toAbsolutePath().normalize();
        if (!sourceDirectory.equals(stateDirectory)) stageRuntime(sourceDirectory, stateDirectory);
        Path staged = stateDirectory.resolve("Mizulune.FantnelHost.exe");
        requireExecutable(staged);
        return new HostLaunch(staged, stateDirectory);
    }

    static Optional<Path> findPreferredHost(Path gameDirectory, Path processDirectory, Path cachedExecutable) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        candidates.add(gameDirectory.resolve("fantnel/Mizulune.FantnelHost.exe").toAbsolutePath().normalize());
        candidates.add(gameDirectory.resolve("mods/fantnel/Mizulune.FantnelHost.exe").toAbsolutePath().normalize());
        candidates.add(processDirectory.resolve("fantnel/Mizulune.FantnelHost.exe").toAbsolutePath().normalize());
        // The persistent copy is only a fallback. Choosing it before the package
        // prevents a new Fabric release from replacing a stale sidecar and can
        // preserve an incompatible named-pipe handshake indefinitely.
        candidates.add(cachedExecutable.toAbsolutePath().normalize());
        return candidates.stream().filter(Files::isRegularFile).findFirst();
    }

    private static void stageRuntime(Path source, Path target) {
        try {
            Files.createDirectories(target);
            try (var entries = Files.walk(source)) {
                for (Path entry : entries.toList()) {
                    Path relative = source.relativize(entry);
                    Path destination = target.resolve(relative).normalize();
                    if (!destination.startsWith(target)) throw new IOException("Fantnel runtime path escapes staging root");
                    if (Files.isDirectory(entry)) {
                        Files.createDirectories(destination);
                    } else if (Files.isRegularFile(entry)) {
                        Files.createDirectories(destination.getParent());
                        if (!Files.isRegularFile(destination)
                            || Files.size(destination) != Files.size(entry)
                            || Files.mismatch(entry, destination) != -1L) {
                            Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                    }
                }
            }
        } catch (IOException error) {
            throw new FantnelHostException("Unable to stage the Fantnel runtime.", error);
        }
    }

    private void readUntilResponse(Process expectedProcess, RandomAccessFile expectedPipe,
                                   CompletableFuture<JsonElement> future) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(1024);
        while (!closing && process == expectedProcess && pipe == expectedPipe && !future.isDone()) {
            int value = expectedPipe.read();
            if (value < 0) throw new IOException("Fantnel pipe closed");
            if (value == '\n') {
                if (line.size() > 0) handleLine(line.toString(StandardCharsets.UTF_8));
                line.reset();
                continue;
            }
            if (value != '\r') line.write(value);
            if (line.size() > MAX_LINE_BYTES) throw new IOException("Fantnel response exceeds the size limit");
        }
    }

    private void handleLine(String raw) {
        JsonObject message;
        try {
            message = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception error) {
            LOGGER.warn("Ignored malformed Fantnel control message");
            return;
        }
        if (message.has("event")) {
            String name = message.get("event").getAsString();
            JsonElement data = message.has("data") ? message.get("data").deepCopy() : new JsonObject();
            notifyEvent(new HostEvent(name, data));
            return;
        }
        if (!message.has("id")) return;
        String id = message.get("id").getAsString();
        PendingRequest request = pending.remove(id);
        if (request == null) return;
        request.timeout().cancel(false);
        if (message.has("ok") && message.get("ok").getAsBoolean()) {
            request.future().complete(message.has("result") ? message.get("result").deepCopy() : new JsonObject());
            return;
        }
        JsonObject error = message.has("error") && message.get("error").isJsonObject()
            ? message.getAsJsonObject("error") : new JsonObject();
        String code = string(error, "code", "fantnel_error");
        String text = redactSensitive(
            sanitize(string(error, "message", "Fantnel operation failed.")),
            request.sensitiveValues());
        request.future().completeExceptionally(new FantnelHostException(code + ": " + text));
    }

    private void writeLine(String value) throws IOException {
        byte[] bytes = (value + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            RandomAccessFile current = pipe;
            if (current == null) throw new IOException("Fantnel control pipe is not connected");
            current.write(bytes);
        }
    }

    private void failRequest(String id, Throwable error) {
        PendingRequest request = pending.remove(id);
        if (request == null) return;
        request.timeout().cancel(false);
        request.future().completeExceptionally(error);
    }

    private void timeoutRequest(String id, String method) {
        if (!pending.containsKey(id)) return;
        TimeoutException error = new TimeoutException("Fantnel request timed out: " + method);
        synchronized (lifecycleLock) {
            if (!pending.containsKey(id)) return;
            cleanupHandles(true);
        }
        for (String pendingId : new ArrayList<>(pending.keySet())) failRequest(pendingId, error);
        notifyEvent(new HostEvent("host.disconnected", new JsonObject()));
    }

    private void handleDisconnect(Process expectedProcess, RandomAccessFile expectedPipe, Throwable error) {
        synchronized (lifecycleLock) {
            if ((expectedProcess != null && process != expectedProcess)
                || (expectedPipe != null && pipe != expectedPipe)) {
                return;
            }
            cleanupHandles(true);
        }
        List<String> ids = new ArrayList<>(pending.keySet());
        for (String id : ids) failRequest(id, error);
        notifyEvent(new HostEvent("host.disconnected", new JsonObject()));
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closing) return;
            closing = true;
            try {
                // A pending native read and a write cannot coexist on this
                // synchronous pipe handle. Only request graceful shutdown when
                // the serialized RPC queue is idle; cleanupHandles remains the
                // bounded fallback for an in-flight operation.
                if (pending.isEmpty() && pipe != null && process != null && process.isAlive()) {
                    JsonObject shutdown = new JsonObject();
                    shutdown.addProperty("id", "mc-shutdown");
                    shutdown.addProperty("method", "host.shutdown");
                    shutdown.add("params", new JsonObject());
                    writeLine(shutdown.toString());
                }
            } catch (Exception ignored) {
            }
            cleanupHandles(true);
            for (String id : new ArrayList<>(pending.keySet())) {
                failRequest(id, new FantnelHostException("Fantnel Host stopped."));
            }
        }
    }

    private void cleanupHandles(boolean stopProcess) {
        RandomAccessFile oldPipe = pipe;
        pipe = null;
        if (oldPipe != null) {
            try {
                oldPipe.close();
            } catch (IOException ignored) {
            }
        }
        Process oldProcess = process;
        process = null;
        if (stopProcess && oldProcess != null && oldProcess.isAlive()) {
            try {
                if (!oldProcess.waitFor(2500, TimeUnit.MILLISECONDS)) {
                    oldProcess.destroy();
                    if (!oldProcess.waitFor(1000, TimeUnit.MILLISECONDS)) oldProcess.destroyForcibly();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                oldProcess.destroyForcibly();
            }
        }
    }

    private static void requireExecutable(Path executable) {
        if (!Files.isRegularFile(executable)) {
            throw new FantnelHostException("Fantnel Host is unavailable: " + executable);
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String sanitize(String value) {
        String text = Objects.toString(value, "Fantnel operation failed.").replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    static Set<String> allowedMethodsForTesting() {
        return ALLOWED_METHODS;
    }

    static String redactSensitive(String value, Set<String> sensitiveValues) {
        String result = sanitize(value);
        for (String sensitive : sensitiveValues) {
            if (sensitive != null && !sensitive.isBlank()) {
                result = result.replace(sensitive, "[redacted]");
            }
        }
        return result;
    }

    private static Set<String> sensitiveValues(JsonElement element) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectSensitiveValues(element, null, values);
        return Set.copyOf(values);
    }

    private static void collectSensitiveValues(JsonElement element, String name, Set<String> values) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry ->
                collectSensitiveValues(entry.getValue(), entry.getKey(), values));
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectSensitiveValues(child, name, values);
            }
            return;
        }
        if (name != null && SENSITIVE_PARAMETER_NAMES.contains(name) && element.isJsonPrimitive()
            && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (!value.isBlank()) values.add(value);
        }
    }

    private void notifyEvent(HostEvent event) {
        for (Consumer<HostEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicLong ids = new AtomicLong();
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public record HostEvent(String name, JsonElement data) {
    }

    private record PendingRequest(CompletableFuture<JsonElement> future, ScheduledFuture<?> timeout,
                                  Set<String> sensitiveValues) {
    }

    private record HostLaunch(Path executable, Path workingDirectory) {
    }

    public static final class FantnelHostException extends RuntimeException {
        public FantnelHostException(String message) {
            super(message);
        }

        public FantnelHostException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
