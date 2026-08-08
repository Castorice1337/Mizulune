/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldBlockItemSelection and ItemStackComparators:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 Scaffold v2 policies.
 */
package shit.zen.modules.impl.movement.scaffold.v2.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class ScaffoldBlockItemSelection {
    public static final int HOTBAR_SIZE = 9;
    public static final int PLAYER_INVENTORY_SIZE = 36;
    public static final boolean DEFAULT_CONSIDER_INVENTORY = false;

    private static final double GOOD_HARDNESS_MIN = 0.8;
    private static final double GOOD_HARDNESS_MAX = 2.0;
    private static final double IDEAL_HARDNESS = 1.7;

    private static final Set<String> DISALLOWED_BLOCK_IDS = Set.of(
            "minecraft:tnt",
            "minecraft:cobweb",
            "minecraft:nether_portal");

    private static final Set<String> UNFAVORABLE_BLOCK_IDS = Set.of(
            "minecraft:crafting_table",
            "minecraft:jigsaw",
            "minecraft:smithing_table",
            "minecraft:fletching_table",
            "minecraft:enchanting_table",
            "minecraft:cauldron",
            "minecraft:magma_block");

    private ScaffoldBlockItemSelection() {
    }

    public static Set<String> disallowedBlockIds() {
        return DISALLOWED_BLOCK_IDS;
    }

    public static Set<String> unfavorableBlockIds() {
        return UNFAVORABLE_BLOCK_IDS;
    }

    public static boolean isDisallowedBlock(Block block) {
        if (block == null) {
            return false;
        }
        return block == Blocks.TNT
                || block == Blocks.COBWEB
                || block == Blocks.NETHER_PORTAL;
    }

    public static boolean isHardcodedUnfavorableBlock(Block block) {
        if (block == null) {
            return false;
        }
        return block == Blocks.CRAFTING_TABLE
                || block == Blocks.JIGSAW
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.CAULDRON
                || block == Blocks.MAGMA_BLOCK;
    }

    public static boolean isValidBlock(ItemStack stack, BlockGetter level, Entity player) {
        return describe(-1, stack, level, player).profile().isValid();
    }

    public static boolean isBlockUnfavorable(ItemStack stack, BlockGetter level, Entity player) {
        return describe(-1, stack, level, player).profile().isUnfavorable();
    }

    public static Candidate<ItemStack> describe(
            int slot,
            ItemStack stack,
            BlockGetter level,
            Entity player) {
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
        if (safeStack.isEmpty() || !(safeStack.getItem() instanceof BlockItem blockItem)) {
            return new Candidate<>(slot, safeStack, Math.max(0, safeStack.getCount()), BlockProfile.nonBlock());
        }

        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");

        Block block = blockItem.getBlock();
        BlockState defaultState = block.defaultBlockState();
        BlockProfile profile = new BlockProfile(
                true,
                defaultState.entityCanStandOnFace(level, BlockPos.ZERO, player, Direction.UP),
                block instanceof FallingBlock,
                isDisallowedBlock(block),
                isHardcodedUnfavorableBlock(block),
                block.getFriction(),
                block.getSpeedFactor(),
                block.getJumpFactor(),
                block instanceof BaseEntityBlock,
                defaultState.isRedstoneConductor(level, BlockPos.ZERO),
                defaultState.isCollisionShapeFullBlock(level, BlockPos.ZERO),
                defaultState.getDestroySpeed(level, BlockPos.ZERO));
        return new Candidate<>(slot, safeStack, Math.max(0, safeStack.getCount()), profile);
    }

    public static <T> List<Candidate<T>> candidates(
            List<Candidate<T>> candidates,
            boolean considerInventory) {
        return candidates(candidates, considerInventory
                ? CandidateScope.CONSIDER_INVENTORY
                : CandidateScope.HOTBAR);
    }

    public static <T> List<Candidate<T>> candidates(
            List<Candidate<T>> candidates,
            CandidateScope scope) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(scope, "scope");

        int upperSlotExclusive = scope == CandidateScope.CONSIDER_INVENTORY
                ? PLAYER_INVENTORY_SIZE
                : HOTBAR_SIZE;
        List<Candidate<T>> result = new ArrayList<>();
        for (Candidate<T> candidate : candidates) {
            if (candidate != null
                    && candidate.slot() >= 0
                    && candidate.slot() < upperSlotExclusive
                    && candidate.profile().isValid()) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    public static boolean canUpdateRotation(
            boolean considerInventory,
            boolean inventoryOpen,
            boolean containerScreenOpen) {
        return !considerInventory || !inventoryOpen && !containerScreenOpen;
    }

    public static <T> Optional<Candidate<T>> selectBestHotbar(
            List<Candidate<T>> candidates,
            int doNotUseBelowCount) {
        if (doNotUseBelowCount < 0 || doNotUseBelowCount > 64) {
            throw new IllegalArgumentException("doNotUseBelowCount must be in 0..64");
        }

        List<Candidate<T>> placeable = candidates(candidates, CandidateScope.HOTBAR);
        Optional<Candidate<T>> aboveThreshold = selectMaximum(
                placeable.stream()
                        .filter(candidate -> candidate.count() > doNotUseBelowCount)
                        .toList(),
                hotbarComparator());
        return aboveThreshold.isPresent()
                ? aboveThreshold
                : selectMaximum(placeable, hotbarComparator());
    }

    public static <T> Optional<Candidate<T>> selectBestInventory(List<Candidate<T>> candidates) {
        return selectMaximum(
                candidates(candidates, CandidateScope.CONSIDER_INVENTORY),
                inventoryComparator());
    }

    public static <T> List<Candidate<T>> sortHotbarBestFirst(List<Candidate<T>> candidates) {
        return candidates(candidates, CandidateScope.HOTBAR).stream()
                .sorted(ScaffoldBlockItemSelection.<T>hotbarComparator().reversed())
                .toList();
    }

    public static <T> List<Candidate<T>> sortInventoryBestFirst(List<Candidate<T>> candidates) {
        return candidates(candidates, CandidateScope.CONSIDER_INVENTORY).stream()
                .sorted(ScaffoldBlockItemSelection.<T>inventoryComparator().reversed())
                .toList();
    }

    public static <T> Comparator<Candidate<T>> hotbarComparator() {
        return (first, second) -> compare(first, second, Ranking.HOTBAR);
    }

    public static <T> Comparator<Candidate<T>> inventoryComparator() {
        return (first, second) -> compare(first, second, Ranking.INVENTORY);
    }

    private static <T> Optional<Candidate<T>> selectMaximum(
            List<Candidate<T>> candidates,
            Comparator<Candidate<T>> comparator) {
        Candidate<T> best = null;
        for (Candidate<T> candidate : candidates) {
            if (best == null || comparator.compare(best, candidate) < 0) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int compare(Candidate<?> first, Candidate<?> second, Ranking ranking) {
        BlockProfile firstProfile = first.profile();
        BlockProfile secondProfile = second.profile();

        int result = Boolean.compare(!firstProfile.isUnfavorable(), !secondProfile.isUnfavorable());
        if (result != 0) {
            return result;
        }

        result = Boolean.compare(firstProfile.solid(), secondProfile.solid());
        if (result != 0) {
            return result;
        }

        result = Boolean.compare(firstProfile.fullCube(), secondProfile.fullCube());
        if (result != 0) {
            return result;
        }

        result = Float.compare(firstProfile.friction(), secondProfile.friction());
        if (result != 0) {
            return result;
        }

        result = Float.compare(
                Math.abs(firstProfile.jumpFactor() - 1.0f),
                Math.abs(secondProfile.jumpFactor() - 1.0f));
        if (result != 0) {
            return result;
        }

        result = Float.compare(
                Math.abs(firstProfile.speedFactor() - 1.0f),
                Math.abs(secondProfile.speedFactor() - 1.0f));
        if (result != 0) {
            return result;
        }

        result = Double.compare(
                neutralHardnessDistance(secondProfile.hardness()),
                neutralHardnessDistance(firstProfile.hardness()));
        if (result != 0) {
            return result;
        }

        result = ranking == Ranking.HOTBAR
                ? Integer.compare(second.count(), first.count())
                : Integer.compare(first.count(), second.count());
        if (result != 0) {
            return result;
        }

        return Double.compare(
                exactHardnessDistance(secondProfile.hardness()),
                exactHardnessDistance(firstProfile.hardness()));
    }

    private static double neutralHardnessDistance(double hardness) {
        if (hardness >= GOOD_HARDNESS_MIN && hardness <= GOOD_HARDNESS_MAX) {
            return 0.0;
        }
        return exactHardnessDistance(hardness);
    }

    private static double exactHardnessDistance(double hardness) {
        return Math.abs(IDEAL_HARDNESS - hardness);
    }

    public enum CandidateScope {
        HOTBAR,
        CONSIDER_INVENTORY
    }

    private enum Ranking {
        HOTBAR,
        INVENTORY
    }

    public record Candidate<T>(int slot, T value, int count, BlockProfile profile) {
        public Candidate {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(profile, "profile");
            if (count < 0) {
                throw new IllegalArgumentException("count must be non-negative");
            }
        }

        public boolean isHotbarSlot() {
            return this.slot >= 0 && this.slot < HOTBAR_SIZE;
        }
    }

    public record BlockProfile(
            boolean blockItem,
            boolean canStandOnTop,
            boolean fallingBlock,
            boolean disallowed,
            boolean hardcodedUnfavorable,
            float friction,
            float speedFactor,
            float jumpFactor,
            boolean blockEntity,
            boolean solid,
            boolean fullCube,
            double hardness) {

        public static BlockProfile nonBlock() {
            return new BlockProfile(
                    false,
                    false,
                    false,
                    false,
                    false,
                    0.6f,
                    1.0f,
                    1.0f,
                    false,
                    false,
                    false,
                    0.0);
        }

        public boolean isValid() {
            return this.blockItem
                    && this.canStandOnTop
                    && !this.fallingBlock
                    && !this.disallowed;
        }

        public boolean isUnfavorable() {
            return !this.blockItem
                    || this.friction > 0.6f
                    || this.speedFactor < 1.0f
                    || this.jumpFactor < 1.0f
                    || this.blockEntity
                    || !this.fullCube
                    || this.hardcodedUnfavorable;
        }
    }
}
