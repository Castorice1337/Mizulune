package shit.zen.platform;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Process-wide loader service installed before {@code ZenClient.bootstrap()}. */
public final class ClientPlatforms {
    private static final Logger LOGGER = LogManager.getLogger(ClientPlatforms.class);
    private static final ClientPlatform FALLBACK = new ClientPlatform() {
        @Override
        public String loaderId() {
            return "vanilla-fallback";
        }
    };

    private static volatile ClientPlatform current = FALLBACK;

    private ClientPlatforms() {
    }

    public static ClientPlatform current() {
        return current;
    }

    public static synchronized void install(ClientPlatform platform) {
        Objects.requireNonNull(platform, "platform");
        ClientPlatform previous = current;
        if (previous != FALLBACK && !previous.loaderId().equals(platform.loaderId())) {
            throw new IllegalStateException(
                "Client platform already installed: " + previous.loaderId());
        }
        current = platform;
        LOGGER.info("Client platform installed: {}", platform.loaderId());
    }
}
