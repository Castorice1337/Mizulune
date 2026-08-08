package shit.zen.modules.impl.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProtocolDefaultsTest {
    @Test
    void anExplicitlyEnabledProtocolModuleDefaultsToLiveOfficialTraffic() {
        assertFalse(Protocol.DEFAULT_OBSERVE_ONLY);
        assertTrue(Protocol.DEFAULT_ALLOW_LIVE_SEND);
    }
}
