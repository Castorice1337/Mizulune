package shit.zen.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import shit.zen.event.impl.StrafeEvent;

final class KeyboardInputPatchTest {
    @Test
    void slowdownDoesNotTurnIntoSneaking() {
        KeyboardInputPatch.AppliedInput applied = KeyboardInputPatch.applyEvent(
                new StrafeEvent(1.0f, -1.0f, false, false),
                true,
                0.3f);

        assertEquals(0.3f, applied.forward());
        assertEquals(-0.3f, applied.strafe());
        assertFalse(applied.sneaking());
    }

    @Test
    void physicalOrModuleSneakIsPreservedAndSlowed() {
        KeyboardInputPatch.AppliedInput applied = KeyboardInputPatch.applyEvent(
                new StrafeEvent(0.8f, 0.4f, true, true),
                false,
                0.25f);

        assertEquals(0.2f, applied.forward());
        assertEquals(0.1f, applied.strafe());
        assertTrue(applied.jumping());
        assertTrue(applied.sneaking());
    }

    @Test
    void ordinaryInputRemainsUnsneakedAndUnscaled() {
        KeyboardInputPatch.AppliedInput applied = KeyboardInputPatch.applyEvent(
                new StrafeEvent(1.0f, 0.5f, false, false),
                false,
                0.3f);

        assertEquals(1.0f, applied.forward());
        assertEquals(0.5f, applied.strafe());
        assertFalse(applied.sneaking());
    }
}
