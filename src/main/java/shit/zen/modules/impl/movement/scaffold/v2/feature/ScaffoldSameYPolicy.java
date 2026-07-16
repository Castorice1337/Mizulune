/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ModuleScaffold SameYMode:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 Scaffold v2 policies.
 */
package shit.zen.modules.impl.movement.scaffold.v2.feature;

import java.util.Objects;
import net.minecraft.core.BlockPos;

public final class ScaffoldSameYPolicy {
    public static final Mode DEFAULT_MODE = Mode.OFF;
    public static final double HYPIXEL_TRIGGER_VELOCITY = -0.15233518685055708;
    public static final double FALLING_MODE_MAX_UPWARD_VELOCITY = 0.2;

    private int placementY;
    private int startY;
    private int jumps;
    private boolean initialized;

    public void reset(int playerBlockY) {
        this.placementY = playerBlockY - 1;
        this.startY = playerBlockY;
        this.jumps = 2;
        this.initialized = true;
    }

    public void onTick(int playerBlockY, boolean onGround, boolean jumpKeyDown) {
        this.ensureInitialized();
        if (onGround) {
            this.placementY = playerBlockY - 1;
            this.jumps++;
        }
        if (jumpKeyDown) {
            this.startY = playerBlockY;
            this.jumps = 2;
        }
    }

    public Decision resolve(BlockPos blockPos, Mode mode, double verticalVelocity) {
        Objects.requireNonNull(blockPos, "blockPos");
        return this.resolve(blockPos.getY(), mode, verticalVelocity);
    }

    public Decision resolve(int blockY, Mode mode, double verticalVelocity) {
        Objects.requireNonNull(mode, "mode");
        this.ensureInitialized();

        return switch (mode) {
            case OFF -> new Decision(blockY - 1, false, false);
            case ON -> new Decision(this.placementY, true, false);
            case FALLING -> verticalVelocity < FALLING_MODE_MAX_UPWARD_VELOCITY
                    ? new Decision(this.placementY, true, false)
                    : new Decision(blockY - 1, false, false);
            case HYPIXEL -> this.resolveHypixel(verticalVelocity);
        };
    }

    public BlockPos resolvePosition(BlockPos blockPos, Mode mode, double verticalVelocity) {
        Decision decision = this.resolve(blockPos, mode, verticalVelocity);
        return new BlockPos(blockPos.getX(), decision.targetY(), blockPos.getZ());
    }

    private Decision resolveHypixel(double verticalVelocity) {
        if (verticalVelocity == HYPIXEL_TRIGGER_VELOCITY && this.jumps >= 2) {
            this.jumps = 0;
            return new Decision(this.startY, true, true);
        }
        return new Decision(this.startY - 1, true, false);
    }

    private void ensureInitialized() {
        if (!this.initialized) {
            throw new IllegalStateException("reset(playerBlockY) must be called before use");
        }
    }

    public State state() {
        this.ensureInitialized();
        return new State(this.placementY, this.startY, this.jumps);
    }

    public enum Mode {
        OFF,
        ON,
        FALLING,
        HYPIXEL
    }

    public record State(int placementY, int startY, int jumps) {
    }

    public record Decision(int targetY, boolean sameYApplied, boolean hypixelTriggered) {
    }
}
