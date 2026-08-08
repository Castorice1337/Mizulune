package shit.zen.platform;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Stable item-classification boundary across the 1.20 and 26.2 item models. */
public final class ItemCompat {
    private ItemCompat() {
    }

    private static ItemStack stack(Item item) { return item == null ? ItemStack.EMPTY : new ItemStack(item); }
    public static boolean isSword(ItemStack stack) { return ClientPlatforms.current().isSword(stack); }
    public static boolean isSword(Item item) { return isSword(stack(item)); }
    public static boolean isPickaxe(ItemStack stack) { return ClientPlatforms.current().isPickaxe(stack); }
    public static boolean isPickaxe(Item item) { return isPickaxe(stack(item)); }
    public static boolean isDigger(ItemStack stack) { return ClientPlatforms.current().isDigger(stack); }
    public static boolean isDigger(Item item) { return isDigger(stack(item)); }
    public static boolean isArmor(ItemStack stack) { return ClientPlatforms.current().isArmor(stack); }
    public static boolean isArmor(Item item) { return isArmor(stack(item)); }
    public static EquipmentSlot armorSlot(ItemStack stack) { return ClientPlatforms.current().armorSlot(stack); }
    public static EquipmentSlot armorSlot(Item item) { return armorSlot(stack(item)); }
    public static int armorDefense(ItemStack stack) { return ClientPlatforms.current().armorDefense(stack); }
    public static int armorTierScore(ItemStack stack) { return ClientPlatforms.current().armorTierScore(stack); }
    public static double attackDamage(ItemStack stack) { return ClientPlatforms.current().attackDamage(stack); }
    public static boolean isBook(Item item) { return ClientPlatforms.current().isBook(item); }
    public static boolean isBowlFood(Item item) { return ClientPlatforms.current().isBowlFood(item); }
    public static boolean isNamedBlock(Item item) { return item instanceof BlockItem; }
}
