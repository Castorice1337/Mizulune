/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldBreezilyTechnique.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.technique;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;

public final class BreezilyTechnique implements Technique {
    private final Settings settings;
    private float lastSideways;
    private long lastAirTime;
    private double currentEdgeDistanceRandom = 0.45;

    public BreezilyTechnique() {
        this(Settings.DEFAULT);
    }

    public BreezilyTechnique(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULT : settings;
    }

    @Override
    public String name() {
        return "Breezily";
    }

    public Settings settings() {
        return this.settings;
    }

    public float lastSideways() {
        return this.lastSideways;
    }

    public long lastAirTime() {
        return this.lastAirTime;
    }

    public double currentEdgeDistance() {
        return this.currentEdgeDistanceRandom;
    }

    public void reset() {
        this.lastSideways = 0.0f;
        this.lastAirTime = 0L;
        this.currentEdgeDistanceRandom = 0.45;
    }

    @Override
    public List<TargetOffset> targetOffsets(TargetInput input) {
        return List.of(new TargetOffset(
                BlockPos.ZERO,
                SearchOffsets.NORMAL,
                TargetPriority.POSITION,
                AimMode.CENTER,
                false));
    }

    @Override
    public DirectionalInput adjustInput(MovementInput input) {
        DirectionalInput directionalInput = input.directionalInput();
        if (!directionalInput.forwards() || input.shiftDown()) {
            return directionalInput;
        }

        if (input.blockBelowAir()) {
            this.lastAirTime = input.nowMillis();
        } else if (input.nowMillis() - this.lastAirTime > 500L) {
            return directionalInput;
        }

        double modX = input.playerX() - Math.floor(input.playerX());
        double modZ = input.playerZ() - Math.floor(input.playerZ());
        double mirroredEdge = 1.0 - this.currentEdgeDistanceRandom;
        float currentSideways = 0.0f;
        switch (Direction.fromYRot(input.playerYaw())) {
            case SOUTH -> {
                if (modX > mirroredEdge) {
                    currentSideways = 1.0f;
                }
                if (modX < this.currentEdgeDistanceRandom) {
                    currentSideways = -1.0f;
                }
            }
            case NORTH -> {
                if (modX > mirroredEdge) {
                    currentSideways = -1.0f;
                }
                if (modX < this.currentEdgeDistanceRandom) {
                    currentSideways = 1.0f;
                }
            }
            case EAST -> {
                if (modZ > mirroredEdge) {
                    currentSideways = -1.0f;
                }
                if (modZ < this.currentEdgeDistanceRandom) {
                    currentSideways = 1.0f;
                }
            }
            case WEST -> {
                if (modZ > mirroredEdge) {
                    currentSideways = 1.0f;
                }
                if (modZ < this.currentEdgeDistanceRandom) {
                    currentSideways = -1.0f;
                }
            }
            default -> {
            }
        }

        if (this.lastSideways != currentSideways && currentSideways != 0.0f) {
            this.lastSideways = currentSideways;
            this.currentEdgeDistanceRandom = TechniqueMath.sample(
                    this.settings.edgeDistanceMin(),
                    this.settings.edgeDistanceMax(),
                    input.randomSample());
        }

        return new DirectionalInput(
                directionalInput.forwards(),
                directionalInput.backwards(),
                this.lastSideways == -1.0f,
                this.lastSideways == 1.0f);
    }

    @Override
    public Rotation rotation(RotationInput input) {
        DirectionalInput rawInput = input.rawInput();
        if (!rawInput.isMoving()) {
            return noInputRotation(input.target());
        }

        float direction = TechniqueMath.movementYaw(input.playerYaw(), rawInput) + 180.0f;
        float movingYaw = TechniqueMath.roundToStep(direction, 45.0f);
        return new Rotation(movingYaw, movingYaw % 90.0f == 0.0f ? 80.0f : 75.6f);
    }

    private static Rotation noInputRotation(BlockPlacementTarget target) {
        if (target == null || target.rotation() == null) {
            return null;
        }
        float axisMovement = (float) (Math.floor(target.rotation().yaw / 90.0f) * 90.0f);
        return new Rotation(axisMovement + 45.0f, 75.0f);
    }

    public record Settings(double edgeDistanceMin, double edgeDistanceMax) {
        public static final Settings DEFAULT = new Settings(0.45, 0.5);

        public Settings {
            if (edgeDistanceMin < 0.25 || edgeDistanceMax > 0.5
                    || edgeDistanceMin > edgeDistanceMax) {
                throw new IllegalArgumentException("edge distance must be an ordered range in [0.25, 0.5]");
            }
        }
    }
}
