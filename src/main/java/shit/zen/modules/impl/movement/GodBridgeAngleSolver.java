/*
 * This file includes logic adapted from LiquidBounce Nextgen:
 * net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.ScaffoldGodBridgeTechnique
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen as a target-angle-only solver. It does not
 * perform smoothing, block placement, or camera/server rotation application.
 */
package shit.zen.modules.impl.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.rotation.Rotation;

final class GodBridgeAngleSolver extends ClientBase {
    private boolean onRightSide;

    Rotation solve(Target target, int forward, int strafe) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (forward == 0 && strafe == 0) {
            if (target == null) {
                return null;
            }
            return this.getRotationForNoInput(target);
        }

        float direction = (float) Math.toDegrees(MovementUtil.getDirectionYaw(mc.player.getYRot(), forward, strafe)) + 180.0f;
        float movingYaw = Math.round(direction / 45.0f) * 45.0f;
        boolean movingStraight = Math.round(movingYaw) % 90 == 0;
        return movingStraight ? this.getRotationForStraightInput(movingYaw) : new Rotation(movingYaw, 75.6f);
    }

    private Rotation getRotationForStraightInput(float movingYaw) {
        if (mc.player.onGround()) {
            double yawRad = Math.toRadians(movingYaw);
            this.onRightSide = Math.floor(mc.player.getX() + Math.cos(yawRad) * 0.5) != Math.floor(mc.player.getX())
                    || Math.floor(mc.player.getZ() + Math.sin(yawRad) * 0.5) != Math.floor(mc.player.getZ());

            Direction direction = Direction.fromYRot(movingYaw);
            Vec3 posInDirection = mc.player.position().relative(direction, 0.6);
            BlockPos blockInDirection = BlockPos.containing(posInDirection);
            boolean leaningOffBlock = mc.level.getBlockState(mc.player.blockPosition().below()).isAir();
            boolean nextBlockIsAir = mc.level.getBlockState(blockInDirection.below()).isAir();

            if (leaningOffBlock && nextBlockIsAir) {
                this.onRightSide = !this.onRightSide;
            }
        }

        return new Rotation(movingYaw + (this.onRightSide ? 45.0f : -45.0f), 75.7f);
    }

    private Rotation getRotationForNoInput(Target target) {
        Rotation targetRotation = new Rotation(mc.player.getEyePosition(), target.hitVec());
        float axisMovement = (float) Math.floor(targetRotation.getYaw() / 90.0f) * 90.0f;
        return new Rotation(axisMovement + 45.0f, 75.0f);
    }

    record Target(Vec3 hitVec) {
    }
}
