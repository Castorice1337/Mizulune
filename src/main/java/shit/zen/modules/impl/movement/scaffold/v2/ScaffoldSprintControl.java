/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldSprintControlFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 sprint events.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import shit.zen.event.impl.SprintDecisionEvent;
import shit.zen.utils.game.DirectionalInput;

public final class ScaffoldSprintControl {
    private boolean wasPlaced;

    public void onGameTick() {
        this.wasPlaced = false;
    }

    public void onBlockPlacement() {
        this.wasPlaced = true;
    }

    public boolean apply(
            boolean currentSprint,
            DirectionalInput input,
            boolean onGround,
            SprintMode clientMode,
            SprintMode serverMode,
            SprintDecisionEvent.Source source) {
        boolean sprint = currentSprint;
        if (source == SprintDecisionEvent.Source.MOVEMENT_TICK
                || source == SprintDecisionEvent.Source.INPUT) {
            sprint = applyMode(sprint, input, onGround, clientMode, this.wasPlaced);
        }
        if (source == SprintDecisionEvent.Source.NETWORK
                || source == SprintDecisionEvent.Source.INPUT) {
            sprint = applyMode(sprint, input, onGround, serverMode, this.wasPlaced);
        }
        return sprint;
    }

    static boolean applyMode(
            boolean currentSprint,
            DirectionalInput input,
            boolean onGround,
            SprintMode mode,
            boolean wasPlaced) {
        if (mode == null) {
            return currentSprint;
        }
        return switch (mode) {
            case DO_NOT_CHANGE -> currentSprint;
            case FORCE_SPRINT -> (input != null && input.isMoving()) || currentSprint;
            case FORCE_NO_SPRINT -> false;
            case NO_SPRINT_ON_PLACE -> wasPlaced ? false : currentSprint;
            case NO_SPRINT_ON_GROUND -> !onGround;
        };
    }

    public boolean wasPlaced() {
        return this.wasPlaced;
    }

    public enum SprintMode {
        DO_NOT_CHANGE,
        FORCE_SPRINT,
        FORCE_NO_SPRINT,
        NO_SPRINT_ON_PLACE,
        NO_SPRINT_ON_GROUND
    }
}
