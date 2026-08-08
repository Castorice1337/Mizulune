package shit.zen.fabric.game;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** 26.2 registry/component adapters used by the generated shared-source view. */
public final class FabricItemCompat {
    private FabricItemCompat() {
    }

    public static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        Registry<Enchantment> registry = minecraft.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> enchantment = registry.get(key.identifier()).orElse(null);
        return enchantment == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    public static boolean isEdible(ItemStack stack) {
        return stack != null && stack.get(net.minecraft.core.component.DataComponents.FOOD) != null;
    }
}
