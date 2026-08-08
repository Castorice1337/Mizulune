package shit.zen.utils.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PlayerPositionHoldPolicyTest {
    @Test
    void positionOnlyKeepsRotationAndStatusPacketsAvailable() {
        PlayerPositionHold.MovePacketPolicy policy =
                PlayerPositionHold.MovePacketPolicy.POSITION_ONLY;

        assertTrue(policy.shouldCancel(true));
        assertFalse(policy.shouldCancel(false));
    }

    @Test
    void blockAllSuppressesEveryMovementPacketShape() {
        PlayerPositionHold.MovePacketPolicy policy =
                PlayerPositionHold.MovePacketPolicy.BLOCK_ALL;

        assertTrue(policy.shouldCancel(true));
        assertTrue(policy.shouldCancel(false));
    }
}
