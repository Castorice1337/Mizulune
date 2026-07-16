/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldLedgeFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a world-independent Scaffold policy.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class ScaffoldLedgePolicy {
    private int forcedSneakTicks;

    public LedgeAction decide(
            boolean closeToEdge,
            int blockCount,
            int rotationEtaTicks,
            LedgeAction extensionAction) {
        return this.evaluate(closeToEdge, blockCount, rotationEtaTicks, extensionAction);
    }

    public LedgeAction decide(
            boolean closeToEdge,
            int blockCount,
            IntSupplier rotationEta,
            Supplier<LedgeAction> extension) {
        return this.evaluate(closeToEdge, blockCount, rotationEta, extension);
    }

    public LedgeAction evaluate(
            boolean closeToEdge,
            int blockCount,
            int rotationEtaTicks,
            LedgeAction extensionAction) {
        return this.evaluate(
                closeToEdge,
                blockCount,
                () -> rotationEtaTicks,
                () -> extensionAction);
    }

    public LedgeAction evaluate(
            boolean closeToEdge,
            int blockCount,
            IntSupplier rotationEta,
            Supplier<LedgeAction> extension) {
        if (closeToEdge) {
            int ticks = rotationEta == null ? 0 : rotationEta.getAsInt();
            boolean lowOnBlocks = blockCount <= 0;
            boolean rotationNotReady = ticks >= 1;
            if (lowOnBlocks || rotationNotReady) {
                return new LedgeAction(false, Math.max(1, ticks), false, false);
            }
        }

        if (extension == null) {
            return LedgeAction.NO_LEDGE;
        }
        LedgeAction action = extension.get();
        return action == null ? LedgeAction.NO_LEDGE : action;
    }

    public boolean consumeForcedSneak() {
        if (this.forcedSneakTicks <= 0) {
            return false;
        }
        this.forcedSneakTicks--;
        return true;
    }

    public boolean requestForcedSneak(int ticks) {
        int requestedTicks = Math.max(0, ticks);
        if (requestedTicks <= this.forcedSneakTicks) {
            return false;
        }
        this.forcedSneakTicks = requestedTicks;
        return requestedTicks > 0;
    }

    public int forcedSneakTicks() {
        return this.forcedSneakTicks;
    }

    public void reset() {
        this.forcedSneakTicks = 0;
    }
}
