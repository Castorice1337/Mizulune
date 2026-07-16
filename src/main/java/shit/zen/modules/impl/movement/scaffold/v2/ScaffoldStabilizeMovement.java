/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldStabilizeMovementFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 input events.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.DirectionalInput;

public final class ScaffoldStabilizeMovement {
    static final double MAX_CENTER_DEVIATION = 0.2;
    static final double MAX_CENTER_DEVIATION_IF_MOVING_TOWARDS = 0.075;

    public DirectionalInput stabilize(
            DirectionalInput currentInput,
            boolean jumping,
            boolean onGround,
            ScaffoldMovementPlanner.MovementLine optimalLine,
            Vec3 playerPosition,
            Vec3 playerVelocity,
            float playerYaw) {
        if (currentInput == null || optimalLine == null || playerPosition == null || playerVelocity == null) {
            return currentInput == null ? DirectionalInput.NONE : currentInput;
        }
        if (jumping && onGround) {
            return currentInput;
        }

        Vec3 nearestPointOnLine = optimalLine.nearestPointTo(playerPosition);
        Vec3 vectorToLine = nearestPointOnLine.subtract(playerPosition);
        Vec3 horizontalVelocity = playerVelocity.multiply(1.0, 0.0, 1.0);
        boolean runningTowardsLine = vectorToLine.dot(horizontalVelocity) > 0.0;
        double maxDeviation = runningTowardsLine
                ? MAX_CENTER_DEVIATION_IF_MOVING_TOWARDS
                : MAX_CENTER_DEVIATION;
        if (nearestPointOnLine.distanceToSqr(playerPosition) < maxDeviation * maxDeviation) {
            return currentInput;
        }

        float degrees = getDegreesRelativeToView(vectorToLine, playerYaw);
        DirectionalInput correction = getDirectionalInputForDegrees(degrees);
        boolean frontalAxisBlocked = currentInput.forwards() || currentInput.backwards();
        boolean sagittalAxisBlocked = currentInput.right() || currentInput.left();
        return new DirectionalInput(
                frontalAxisBlocked ? currentInput.forwards() : correction.forwards(),
                frontalAxisBlocked ? currentInput.backwards() : correction.backwards(),
                sagittalAxisBlocked ? currentInput.left() : correction.left(),
                sagittalAxisBlocked ? currentInput.right() : correction.right());
    }

    static float getDegreesRelativeToView(Vec3 relativePosition, float yaw) {
        float optimalYaw = (float) Math.atan2(-relativePosition.x, relativePosition.z);
        float currentYaw = Mth.wrapDegrees(yaw) * Mth.DEG_TO_RAD;
        return Mth.wrapDegrees((optimalYaw - currentYaw) / Mth.DEG_TO_RAD);
    }

    static DirectionalInput getDirectionalInputForDegrees(float degrees) {
        boolean forwards = false;
        boolean backwards = false;
        boolean left = false;
        boolean right = false;

        if (degrees >= -90.0f && degrees <= 90.0f) {
            forwards = true;
        } else if (degrees < -90.0f || degrees > 90.0f) {
            backwards = true;
        }

        if (degrees >= 0.0f && degrees <= 180.0f) {
            right = true;
        } else if (degrees >= -180.0f && degrees <= 0.0f) {
            left = true;
        }
        return new DirectionalInput(forwards, backwards, left, right);
    }
}
