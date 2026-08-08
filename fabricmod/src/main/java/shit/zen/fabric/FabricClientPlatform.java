package shit.zen.fabric;

import java.nio.file.Path;
import com.mojang.blaze3d.opengl.GlBackend;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import shit.zen.platform.ClientPlatform;

/** Fabric implementation of the small shared loader boundary. */
public final class FabricClientPlatform implements ClientPlatform {
    public static final FabricClientPlatform INSTANCE = new FabricClientPlatform();

    private FabricClientPlatform() {
    }

    @Override
    public String loaderId() {
        return "fabric-mixin";
    }

    @Override
    public Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }

    @Override
    public boolean supportsLegacyOpenGlRendering() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
            && minecraft.getWindow() != null
            && minecraft.getWindow().backend() instanceof GlBackend;
    }

    @Override
    public boolean supportsOfficialId114Native() {
        // The packaged sink is pinned to the Forge/ASM Java 17 jvm.dll ABI.
        // Fabric 26.2 runs Java 25 and therefore remains metadata-only for ID114.
        return false;
    }

    @Override
    public void swapInventorySlotWithOffhand(int inventorySlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) return;
        int menuSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
        minecraft.gameMode.handleContainerInput(
            minecraft.player.inventoryMenu.containerId,
            menuSlot,
            40,
            ContainerInput.SWAP,
            minecraft.player
        );
    }

    @Override
    public int heldSlot(Object packet) {
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket held) {
            return held.slot();
        }
        if (packet instanceof net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket held) {
            return held.getSlot();
        }
        return -1;
    }

    @Override public boolean isSword(ItemStack stack) { return stack != null && stack.is(ItemTags.SWORDS); }
    @Override public boolean isPickaxe(ItemStack stack) { return stack != null && stack.is(ItemTags.PICKAXES); }
    @Override public boolean isDigger(ItemStack stack) {
        return stack != null && (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
            || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES));
    }
    @Override public boolean isArmor(ItemStack stack) {
        return armorSlot(stack) != null;
    }
    @Override public EquipmentSlot armorSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return null;
        EquipmentSlot slot = equippable.slot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET ? slot : null;
    }
    @Override public int armorDefense(ItemStack stack) {
        EquipmentSlot slot = armorSlot(stack);
        if (slot == null) return 0;
        ItemAttributeModifiers modifiers = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return (int) Math.round(modifiers.compute(Attributes.ARMOR, 0.0, slot));
    }
    @Override public int armorTierScore(ItemStack stack) {
        if (!isArmor(stack)) return 0;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("netherite")) return 600;
        if (path.contains("diamond")) return 500;
        if (path.contains("iron")) return 400;
        if (path.contains("gold")) return 300;
        if (path.contains("chain")) return 200;
        if (path.contains("leather")) return 100;
        return armorDefense(stack) * 50;
    }
    @Override public double attackDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        ItemAttributeModifiers modifiers = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
    }
    @Override public boolean isBook(Item item) { return item == Items.BOOK; }
    @Override public boolean isBowlFood(Item item) {
        return item == Items.MUSHROOM_STEW || item == Items.RABBIT_STEW
            || item == Items.BEETROOT_SOUP || item == Items.SUSPICIOUS_STEW;
    }
}
