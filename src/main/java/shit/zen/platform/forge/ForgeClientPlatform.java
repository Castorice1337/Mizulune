package shit.zen.platform.forge;

import java.nio.file.Path;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BookItem;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.loading.FMLPaths;
import shit.zen.platform.ClientPlatform;
import shit.zen.platform.ClientPlatforms;
import shit.zen.asm.Bootstrap;
import shit.zen.utils.misc.PacketUtil;

/** Forge-only event/path bridge; never enters the Fabric source set. */
public final class ForgeClientPlatform implements ClientPlatform {
    public static final ForgeClientPlatform INSTANCE = new ForgeClientPlatform();

    private ForgeClientPlatform() {
    }

    public static void install() {
        Bootstrap.init();
        ClientPlatforms.install(INSTANCE);
    }

    @Override
    public String loaderId() {
        return "forge-asm";
    }

    @Override
    public Path gameDirectory() {
        try {
            return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        } catch (RuntimeException | LinkageError ignored) {
            return ClientPlatform.super.gameDirectory();
        }
    }

    @Override
    public void beforeMouseButton(int button, int action, int modifiers) {
        ForgeHooksClient.onMouseButtonPre(button, action, modifiers);
    }

    @Override
    public void afterMouseButton(int button, int action, int modifiers) {
        ForgeHooksClient.onMouseButtonPost(button, action, modifiers);
    }

    @Override
    public InteractionResult onItemRightClick(Player player, InteractionHand hand) {
        return ForgeHooks.onItemRightClick(player, hand);
    }

    @Override
    public void onPlayerDestroyItem(Player player, ItemStack stack, InteractionHand hand) {
        ForgeEventFactory.onPlayerDestroyItem(player, stack, hand);
    }

    @Override
    public void swapInventorySlotWithOffhand(int inventorySlot) {
        PacketUtil.sendQueued(new ServerboundPickItemPacket(inventorySlot));
        PacketUtil.sendQueued(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ZERO,
            Direction.DOWN
        ));
    }

    @Override
    public int heldSlot(Object packet) {
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket held) {
            return held.getSlot();
        }
        if (packet instanceof net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket held) {
            return held.getSlot();
        }
        return -1;
    }

    @Override public boolean isSword(ItemStack stack) { return stack != null && stack.getItem() instanceof SwordItem; }
    @Override public boolean isPickaxe(ItemStack stack) { return stack != null && stack.getItem() instanceof PickaxeItem; }
    @Override public boolean isDigger(ItemStack stack) { return stack != null && stack.getItem() instanceof DiggerItem; }
    @Override public boolean isArmor(ItemStack stack) { return stack != null && stack.getItem() instanceof ArmorItem; }
    @Override public EquipmentSlot armorSlot(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : null;
    }
    @Override public int armorDefense(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ArmorItem armor ? armor.getDefense() : 0;
    }
    @Override public int armorTierScore(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ArmorItem armor)) return 0;
        String material = armor.getMaterial().toString().toLowerCase(java.util.Locale.ROOT);
        if (material.contains("netherite")) return 600;
        if (material.contains("diamond")) return 500;
        if (material.contains("iron")) return 400;
        if (material.contains("gold")) return 300;
        if (material.contains("chain")) return 200;
        if (material.contains("leather")) return 100;
        return armor.getDefense() * 50;
    }
    @Override public double attackDamage(ItemStack stack) {
        return stack != null && stack.getItem() instanceof SwordItem sword ? sword.getDamage() + 1.0 : 0.0;
    }
    @Override public boolean isBook(Item item) { return item instanceof BookItem; }
    @Override public boolean isBowlFood(Item item) { return item instanceof BowlFoodItem; }

    @Override
    public String remapMethod(String ownerInternalName, String name, String descriptor) {
        return Bootstrap.remapMethod(ownerInternalName, name, descriptor);
    }

    @Override
    public String remapField(String ownerInternalName, String name) {
        return Bootstrap.remapField(ownerInternalName, name);
    }
}
