/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldEagleFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as an immutable-state Java strategy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.normal;

import java.util.Objects;
import java.util.random.RandomGenerator;
import shit.zen.utils.game.DirectionalInput;

public final class ScaffoldEagleFeature {
    public static final int MIN_BLOCKS_TO_EAGLE = 0;
    public static final int MAX_BLOCKS_TO_EAGLE = 10;
    public static final double MIN_EDGE_DISTANCE = 0.01;
    public static final double MAX_EDGE_DISTANCE = 1.3;
    public static final Settings DEFAULTS = new Settings(
            false,
            new BlocksToEagleRange(0, 0),
            0.01,
            true);
    public static final State DEFAULT_STATE = new State(0, 0);

    private ScaffoldEagleFeature() {
    }

    public static State reset(Settings settings, RandomGenerator random) {
        Objects.requireNonNull(settings, "settings");
        return new State(0, settings.blocksToEagle().sample(random));
    }

    public static State refresh(Settings settings, State state, RandomGenerator random) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(state, "state");
        return new State(state.placedBlocks(), settings.blocksToEagle().sample(random));
    }

    public static Decision decide(
            Settings settings,
            State state,
            Frame frame,
            EdgeProbe edgeProbe) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(edgeProbe, "edgeProbe");

        if (!settings.enabled()
                || frame.downShouldFallOffBlock()
                || settings.onlyOnGround() && !frame.onGround()
                || frame.flying()
                || state.placedBlocks() != 0) {
            return new Decision(frame.sneak(), false, false);
        }

        boolean shouldEagle = edgeProbe.isCloseToEdge(
                frame.directionalInput(),
                settings.edgeDistance());
        return new Decision(frame.sneak() || shouldEagle, shouldEagle, true);
    }

    public static PlacementTransition onBlockPlacement(
            Settings settings,
            State state,
            RandomGenerator random) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(random, "random");

        if (!settings.enabled()) {
            return new PlacementTransition(state, false);
        }

        int placedBlocks = state.placedBlocks() + 1;
        if (placedBlocks > state.currentBlocksToEagle()) {
            return new PlacementTransition(
                    new State(0, settings.blocksToEagle().sample(random)),
                    true);
        }
        return new PlacementTransition(
                new State(placedBlocks, state.currentBlocksToEagle()),
                false);
    }

    @FunctionalInterface
    public interface EdgeProbe {
        boolean isCloseToEdge(DirectionalInput input, double edgeDistance);
    }

    public record BlocksToEagleRange(int minimum, int maximum) {
        public BlocksToEagleRange {
            if (minimum < MIN_BLOCKS_TO_EAGLE
                    || maximum > MAX_BLOCKS_TO_EAGLE
                    || minimum > maximum) {
                throw new IllegalArgumentException("blocksToEagle must be an ordered range in 0..10");
            }
        }

        public int sample(RandomGenerator random) {
            Objects.requireNonNull(random, "random");
            return this.minimum == this.maximum
                    ? this.minimum
                    : random.nextInt(this.minimum, this.maximum + 1);
        }
    }

    public record Settings(
            boolean enabled,
            BlocksToEagleRange blocksToEagle,
            double edgeDistance,
            boolean onlyOnGround) {
        public Settings {
            Objects.requireNonNull(blocksToEagle, "blocksToEagle");
            if (!Double.isFinite(edgeDistance)
                    || edgeDistance < MIN_EDGE_DISTANCE
                    || edgeDistance > MAX_EDGE_DISTANCE) {
                throw new IllegalArgumentException("edgeDistance must be in 0.01..1.3");
            }
        }
    }

    public record State(int placedBlocks, int currentBlocksToEagle) {
        public State {
            if (placedBlocks < 0 || placedBlocks > MAX_BLOCKS_TO_EAGLE) {
                throw new IllegalArgumentException("placedBlocks must be in 0..10");
            }
            if (currentBlocksToEagle < MIN_BLOCKS_TO_EAGLE
                    || currentBlocksToEagle > MAX_BLOCKS_TO_EAGLE) {
                throw new IllegalArgumentException("currentBlocksToEagle must be in 0..10");
            }
        }
    }

    public record Frame(
            DirectionalInput directionalInput,
            boolean sneak,
            boolean downShouldFallOffBlock,
            boolean onGround,
            boolean flying) {
        public Frame {
            directionalInput = directionalInput == null
                    ? DirectionalInput.NONE
                    : directionalInput;
        }
    }

    public record Decision(boolean sneak, boolean shouldEagle, boolean edgeChecked) {
    }

    public record PlacementTransition(State state, boolean refreshed) {
        public PlacementTransition {
            Objects.requireNonNull(state, "state");
        }
    }
}
