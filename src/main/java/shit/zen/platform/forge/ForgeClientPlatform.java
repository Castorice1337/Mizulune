package shit.zen.platform.forge;

import java.nio.file.Path;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.loading.FMLPaths;
import shit.zen.platform.ClientPlatform;
import shit.zen.platform.ClientPlatforms;
import shit.zen.asm.Bootstrap;

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
    public String remapMethod(String ownerInternalName, String name, String descriptor) {
        return Bootstrap.remapMethod(ownerInternalName, name, descriptor);
    }

    @Override
    public String remapField(String ownerInternalName, String name) {
        return Bootstrap.remapField(ownerInternalName, name);
    }
}
