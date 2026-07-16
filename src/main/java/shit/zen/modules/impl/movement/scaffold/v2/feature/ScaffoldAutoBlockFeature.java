/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldAutoBlockFeature and ModuleScaffold:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 Scaffold v2 policies.
 */
package shit.zen.modules.impl.movement.scaffold.v2.feature;

import java.util.List;
import java.util.Objects;

public final class ScaffoldAutoBlockFeature {
    public static final int MIN_SLOT_RESET_DELAY = 0;
    public static final int MAX_SLOT_RESET_DELAY = 40;
    public static final int MIN_DO_NOT_USE_BELOW_COUNT = 0;
    public static final int MAX_DO_NOT_USE_BELOW_COUNT = 64;
    public static final Settings DEFAULTS = new Settings(true, false, 5, 1);

    private ScaffoldAutoBlockFeature() {
    }

    public static SelectionTiming selectionTiming(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        return settings.always()
                ? SelectionTiming.BEFORE_TARGET_VALIDATION
                : SelectionTiming.AFTER_TARGET_VALIDATION;
    }

    public static <T> Decision<T> decide(
            Settings settings,
            boolean hasValidMainHandBlock,
            boolean hasValidOffHandBlock,
            List<ScaffoldBlockItemSelection.Candidate<T>> candidates) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(candidates, "candidates");

        if (settings.enabled() && !hasValidMainHandBlock && !hasValidOffHandBlock) {
            return ScaffoldBlockItemSelection.selectBestHotbar(
                            candidates,
                            settings.doNotUseBelowCount())
                    .map(candidate -> new Decision<>(
                            Action.SELECT_SLOT,
                            candidate,
                            settings.slotResetDelay(),
                            true))
                    .orElseGet(() -> new Decision<>(
                            Action.RESET_SLOT,
                            null,
                            settings.slotResetDelay(),
                            false));
        }

        return new Decision<>(
                Action.RESET_SLOT,
                null,
                settings.slotResetDelay(),
                hasValidMainHandBlock);
    }

    public record Settings(
            boolean enabled,
            boolean always,
            int slotResetDelay,
            int doNotUseBelowCount) {
        public Settings {
            if (slotResetDelay < MIN_SLOT_RESET_DELAY || slotResetDelay > MAX_SLOT_RESET_DELAY) {
                throw new IllegalArgumentException("slotResetDelay must be in 0..40");
            }
            if (doNotUseBelowCount < MIN_DO_NOT_USE_BELOW_COUNT
                    || doNotUseBelowCount > MAX_DO_NOT_USE_BELOW_COUNT) {
                throw new IllegalArgumentException("doNotUseBelowCount must be in 0..64");
            }
        }
    }

    public record Decision<T>(
            Action action,
            ScaffoldBlockItemSelection.Candidate<T> candidate,
            int slotResetDelay,
            boolean hasValidMainHandBlock) {
        public Decision {
            Objects.requireNonNull(action, "action");
            if (action == Action.SELECT_SLOT && candidate == null) {
                throw new IllegalArgumentException("SELECT_SLOT requires a candidate");
            }
            if (action == Action.RESET_SLOT && candidate != null) {
                throw new IllegalArgumentException("RESET_SLOT cannot carry a candidate");
            }
        }

        public int selectedSlot() {
            return this.candidate == null ? -1 : this.candidate.slot();
        }
    }

    public enum Action {
        SELECT_SLOT,
        RESET_SLOT
    }

    public enum SelectionTiming {
        BEFORE_TARGET_VALIDATION,
        AFTER_TARGET_VALIDATION
    }
}
