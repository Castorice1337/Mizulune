package shit.zen.modules.impl.movement.scaffold.v2.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ScaffoldBlockItemSelectionTest {
    @Test
    void hardcodedBlockSetsMatchLiquidSourceDefaults() {
        assertEquals(Set.of("minecraft:tnt", "minecraft:cobweb", "minecraft:nether_portal"),
                ScaffoldBlockItemSelection.disallowedBlockIds());
        assertEquals(Set.of(
                        "minecraft:crafting_table",
                        "minecraft:jigsaw",
                        "minecraft:smithing_table",
                        "minecraft:fletching_table",
                        "minecraft:enchanting_table",
                        "minecraft:cauldron",
                        "minecraft:magma_block"),
                ScaffoldBlockItemSelection.unfavorableBlockIds());
    }

    @Test
    void validityRejectsNonBlocksUnsafeTopFallingAndDisallowedProfiles() {
        assertFalse(ScaffoldBlockItemSelection.BlockProfile.nonBlock().isValid());
        assertFalse(profile(false, false, false, true, true, 0.6f, 1.0f, 1.0f, false, 1.5).isValid());
        assertFalse(profile(true, true, false, true, true, 0.6f, 1.0f, 1.0f, false, 1.5).isValid());
        assertFalse(profile(true, false, true, true, true, 0.6f, 1.0f, 1.0f, false, 1.5).isValid());
        assertTrue(profile(true, false, false, true, true, 0.6f, 1.0f, 1.0f, false, 1.5).isValid());
    }

    @Test
    void unfavorableChecksCoverLiquidSourceDynamicAndHardcodedRules() {
        assertFalse(profile(true, false, false, true, true, 0.6f, 1.0f, 1.0f, false, 1.5).isUnfavorable());
        assertTrue(profile(true, false, false, true, true, 0.61f, 1.0f, 1.0f, false, 1.5).isUnfavorable());
        assertTrue(profile(true, false, false, true, true, 0.6f, 0.99f, 1.0f, false, 1.5).isUnfavorable());
        assertTrue(profile(true, false, false, true, true, 0.6f, 1.0f, 0.99f, false, 1.5).isUnfavorable());
        assertTrue(profile(true, false, false, true, true, 0.6f, 1.0f, 1.0f, true, 1.5).isUnfavorable());
        assertTrue(profile(true, false, false, true, false, 0.6f, 1.0f, 1.0f, false, 1.5).isUnfavorable());
        assertTrue(new ScaffoldBlockItemSelection.BlockProfile(
                true,
                true,
                false,
                false,
                true,
                0.6f,
                1.0f,
                1.0f,
                false,
                true,
                true,
                1.5).isUnfavorable());
    }

    @Test
    void candidateScopeUsesHotbarByDefaultAndInventoryWhenRequested() {
        List<ScaffoldBlockItemSelection.Candidate<String>> candidates = List.of(
                candidate(0, "hotbar", 16, favorableProfile()),
                candidate(9, "inventory", 16, favorableProfile()),
                candidate(35, "last", 16, favorableProfile()),
                candidate(36, "outside", 16, favorableProfile()),
                candidate(1, "invalid", 16, ScaffoldBlockItemSelection.BlockProfile.nonBlock()));

        assertEquals(List.of("hotbar"), ScaffoldBlockItemSelection.candidates(candidates, false).stream()
                .map(ScaffoldBlockItemSelection.Candidate::value)
                .toList());
        assertEquals(List.of("hotbar", "inventory", "last"),
                ScaffoldBlockItemSelection.candidates(candidates, true).stream()
                        .map(ScaffoldBlockItemSelection.Candidate::value)
                        .toList());
    }

    @Test
    void considerInventoryOnlyBlocksRotationForOpenInventoryScreens() {
        assertTrue(ScaffoldBlockItemSelection.canUpdateRotation(false, true, true));
        assertFalse(ScaffoldBlockItemSelection.canUpdateRotation(true, true, false));
        assertFalse(ScaffoldBlockItemSelection.canUpdateRotation(true, false, true));
        assertTrue(ScaffoldBlockItemSelection.canUpdateRotation(true, false, false));
    }

    @Test
    void hotbarSelectionUsesThresholdBeforeComparatorFallback() {
        List<ScaffoldBlockItemSelection.Candidate<String>> candidates = List.of(
                candidate(0, "one", 1, favorableProfile()),
                candidate(1, "large", 64, favorableProfile()));

        assertEquals("large", ScaffoldBlockItemSelection.selectBestHotbar(candidates, 1)
                .orElseThrow()
                .value());
        assertEquals("one", ScaffoldBlockItemSelection.selectBestHotbar(candidates, 64)
                .orElseThrow()
                .value());
    }

    @Test
    void comparatorChainPrioritizesFavorableBeforeLaterCriteria() {
        ScaffoldBlockItemSelection.BlockProfile unfavorableSolid = profile(
                true, false, false, true, true, 0.61f, 1.0f, 1.0f, false, 1.5);
        ScaffoldBlockItemSelection.BlockProfile favorableNonSolid = profile(
                true, false, false, false, true, 0.6f, 1.0f, 1.0f, false, 1.5);
        List<ScaffoldBlockItemSelection.Candidate<String>> sorted =
                ScaffoldBlockItemSelection.sortHotbarBestFirst(List.of(
                        candidate(0, "unfavorable", 64, unfavorableSolid),
                        candidate(1, "favorable", 2, favorableNonSolid)));

        assertEquals(List.of("favorable", "unfavorable"), sorted.stream()
                .map(ScaffoldBlockItemSelection.Candidate::value)
                .toList());
    }

    @Test
    void inventoryComparatorKeepsLargerEquivalentStack() {
        List<ScaffoldBlockItemSelection.Candidate<String>> candidates = List.of(
                candidate(9, "small", 2, favorableProfile()),
                candidate(10, "large", 64, favorableProfile()));

        assertEquals("large", ScaffoldBlockItemSelection.selectBestInventory(candidates)
                .orElseThrow()
                .value());
    }

    private static ScaffoldBlockItemSelection.Candidate<String> candidate(
            int slot,
            String value,
            int count,
            ScaffoldBlockItemSelection.BlockProfile profile) {
        return new ScaffoldBlockItemSelection.Candidate<>(slot, value, count, profile);
    }

    private static ScaffoldBlockItemSelection.BlockProfile favorableProfile() {
        return profile(true, false, false, true, true, 0.6f, 1.0f, 1.0f, false, 1.5);
    }

    private static ScaffoldBlockItemSelection.BlockProfile profile(
            boolean canStandOnTop,
            boolean fallingBlock,
            boolean disallowed,
            boolean solid,
            boolean fullCube,
            float friction,
            float speedFactor,
            float jumpFactor,
            boolean blockEntity,
            double hardness) {
        return new ScaffoldBlockItemSelection.BlockProfile(
                true,
                canStandOnTop,
                fallingBlock,
                disallowed,
                false,
                friction,
                speedFactor,
                jumpFactor,
                blockEntity,
                solid,
                fullCube,
                hardness);
    }
}
