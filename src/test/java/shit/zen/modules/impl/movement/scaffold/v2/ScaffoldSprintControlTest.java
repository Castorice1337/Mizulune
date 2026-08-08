package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import shit.zen.event.impl.SprintDecisionEvent;
import shit.zen.utils.game.DirectionalInput;

final class ScaffoldSprintControlTest {
    private static final DirectionalInput SIDEWAYS =
            new DirectionalInput(false, false, true, false);

    @Test
    void forceSprintAllowsCorrectedSidewaysInput() {
        ScaffoldSprintControl control = new ScaffoldSprintControl();

        assertTrue(control.apply(
                false,
                SIDEWAYS,
                true,
                ScaffoldSprintControl.SprintMode.FORCE_SPRINT,
                ScaffoldSprintControl.SprintMode.DO_NOT_CHANGE,
                SprintDecisionEvent.Source.MOVEMENT_TICK));
    }

    @Test
    void serverModeCanOverrideInputSourceClientDecision() {
        ScaffoldSprintControl control = new ScaffoldSprintControl();

        assertFalse(control.apply(
                false,
                SIDEWAYS,
                true,
                ScaffoldSprintControl.SprintMode.FORCE_SPRINT,
                ScaffoldSprintControl.SprintMode.FORCE_NO_SPRINT,
                SprintDecisionEvent.Source.INPUT));
    }

    @Test
    void noSprintOnPlaceLastsUntilNextGameTick() {
        ScaffoldSprintControl control = new ScaffoldSprintControl();
        control.onBlockPlacement();

        assertFalse(control.apply(
                true,
                SIDEWAYS,
                false,
                ScaffoldSprintControl.SprintMode.NO_SPRINT_ON_PLACE,
                ScaffoldSprintControl.SprintMode.DO_NOT_CHANGE,
                SprintDecisionEvent.Source.MOVEMENT_TICK));
        control.onGameTick();
        assertTrue(control.apply(
                true,
                SIDEWAYS,
                false,
                ScaffoldSprintControl.SprintMode.NO_SPRINT_ON_PLACE,
                ScaffoldSprintControl.SprintMode.DO_NOT_CHANGE,
                SprintDecisionEvent.Source.MOVEMENT_TICK));
    }
}
