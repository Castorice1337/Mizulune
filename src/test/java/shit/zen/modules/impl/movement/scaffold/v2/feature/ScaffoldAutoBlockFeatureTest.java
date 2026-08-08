package shit.zen.modules.impl.movement.scaffold.v2.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ScaffoldAutoBlockFeatureTest {
    @Test
    void defaultsMatchLiquidSource() {
        assertTrue(ScaffoldAutoBlockFeature.DEFAULTS.enabled());
        assertFalse(ScaffoldAutoBlockFeature.DEFAULTS.always());
        assertEquals(5, ScaffoldAutoBlockFeature.DEFAULTS.slotResetDelay());
        assertEquals(1, ScaffoldAutoBlockFeature.DEFAULTS.doNotUseBelowCount());
        assertEquals(
                ScaffoldAutoBlockFeature.SelectionTiming.AFTER_TARGET_VALIDATION,
                ScaffoldAutoBlockFeature.selectionTiming(ScaffoldAutoBlockFeature.DEFAULTS));
    }

    @Test
    void alwaysMovesSelectionBeforeTargetValidation() {
        ScaffoldAutoBlockFeature.Settings settings = new ScaffoldAutoBlockFeature.Settings(true, true, 5, 1);

        assertEquals(
                ScaffoldAutoBlockFeature.SelectionTiming.BEFORE_TARGET_VALIDATION,
                ScaffoldAutoBlockFeature.selectionTiming(settings));
    }

    @Test
    void selectsBestHotbarCandidateOnlyWhenBothHandsAreInvalid() {
        ScaffoldAutoBlockFeature.Decision<String> decision = ScaffoldAutoBlockFeature.decide(
                ScaffoldAutoBlockFeature.DEFAULTS,
                false,
                false,
                List.of(candidate(0, "one", 1), candidate(4, "blocks", 32)));

        assertEquals(ScaffoldAutoBlockFeature.Action.SELECT_SLOT, decision.action());
        assertEquals(4, decision.selectedSlot());
        assertEquals(5, decision.slotResetDelay());
        assertTrue(decision.hasValidMainHandBlock());
    }

    @Test
    void validOffhandOrDisabledAutoBlockResetsSilentSlot() {
        ScaffoldAutoBlockFeature.Decision<String> offhandDecision = ScaffoldAutoBlockFeature.decide(
                ScaffoldAutoBlockFeature.DEFAULTS,
                false,
                true,
                List.of(candidate(4, "blocks", 32)));
        ScaffoldAutoBlockFeature.Decision<String> disabledDecision = ScaffoldAutoBlockFeature.decide(
                new ScaffoldAutoBlockFeature.Settings(false, false, 5, 1),
                false,
                false,
                List.of(candidate(4, "blocks", 32)));

        assertEquals(ScaffoldAutoBlockFeature.Action.RESET_SLOT, offhandDecision.action());
        assertFalse(offhandDecision.hasValidMainHandBlock());
        assertEquals(ScaffoldAutoBlockFeature.Action.RESET_SLOT, disabledDecision.action());
    }

    @Test
    void settingBoundsAreInclusiveAndRejectOutOfRangeValues() {
        new ScaffoldAutoBlockFeature.Settings(true, false, 0, 0);
        new ScaffoldAutoBlockFeature.Settings(true, false, 40, 64);

        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldAutoBlockFeature.Settings(true, false, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldAutoBlockFeature.Settings(true, false, 41, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldAutoBlockFeature.Settings(true, false, 5, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldAutoBlockFeature.Settings(true, false, 5, 65));
    }

    private static ScaffoldBlockItemSelection.Candidate<String> candidate(
            int slot,
            String value,
            int count) {
        return new ScaffoldBlockItemSelection.Candidate<>(
                slot,
                value,
                count,
                new ScaffoldBlockItemSelection.BlockProfile(
                        true,
                        true,
                        false,
                        false,
                        false,
                        0.6f,
                        1.0f,
                        1.0f,
                        false,
                        true,
                        true,
                        1.5));
    }
}
