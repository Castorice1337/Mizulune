/*
 * This file includes event semantics adapted from LiquidBounce SprintEvent.
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified in 2026 for Mizulune/OpenZen's Java/Forge 1.20.1 event bus.
 */
package shit.zen.event.impl;

import shit.zen.event.EventMarker;
import shit.zen.utils.game.DirectionalInput;

public final class SprintDecisionEvent implements EventMarker {
    private final DirectionalInput directionalInput;
    private final Source source;
    private boolean sprinting;

    public SprintDecisionEvent(
            DirectionalInput directionalInput,
            boolean sprinting,
            Source source) {
        this.directionalInput = directionalInput == null
                ? DirectionalInput.NONE
                : directionalInput;
        this.sprinting = sprinting;
        this.source = source;
    }

    public DirectionalInput getDirectionalInput() {
        return this.directionalInput;
    }

    public boolean isSprinting() {
        return this.sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public Source getSource() {
        return this.source;
    }

    public enum Source {
        INPUT,
        MOVEMENT_TICK,
        NETWORK
    }
}
