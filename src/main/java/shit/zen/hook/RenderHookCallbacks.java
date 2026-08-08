package shit.zen.hook;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import shit.zen.ZenClient;
import shit.zen.event.impl.RenderEvent;
import shit.zen.hud.ScoreboardHud;
import shit.zen.modules.impl.render.DynamicIsland;
import shit.zen.modules.impl.render.NameTags;
import shit.zen.modules.impl.render.NoRender;

/** Shared world/HUD render decisions for Patchify and Mixin. */
public final class RenderHookCallbacks {
    private RenderHookCallbacks() {
    }

    public static HookDecision<Void> onContainerRender(ContainerScreen screen) {
        return ZenClient.isReady() && DynamicIsland.shouldSuppressChestScreen(screen)
                ? HookDecision.cancel()
                : HookDecision.pass();
    }

    public static HookDecision<Void> onRenderNameTag(Entity entity) {
        return entity instanceof LivingEntity
                && ZenClient.isReady()
                && NameTags.INSTANCE != null
                && NameTags.INSTANCE.isEnabled()
                ? HookDecision.cancel()
                : HookDecision.pass();
    }

    public static void onRenderLevel(PoseStack poseStack, float partialTick) {
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new RenderEvent(poseStack, partialTick));
        }
    }

    public static HookDecision<Void> onScoreboardSidebar() {
        return ScoreboardHud.shouldCancelVanillaSidebar()
                ? HookDecision.cancel()
                : HookDecision.pass();
    }

    public static HookDecision<Void> onTextureOverlay(ResourceLocation texture) {
        return NoRender.shouldCancelTextureOverlay(texture)
                ? HookDecision.cancel()
                : HookDecision.pass();
    }

    public static HookDecision<Void> onPortalOverlay() {
        return NoRender.shouldHidePortalOverlay()
                ? HookDecision.cancel()
                : HookDecision.pass();
    }

    public static HookDecision<Float> onDarknessScale() {
        return NoRender.shouldHideDarkness()
                ? HookDecision.handled(0.0f)
                : HookDecision.pass();
    }

    public static HookDecision<Void> onFireOverlay() {
        return NoRender.shouldHideFireOverlay()
                ? HookDecision.cancel()
                : HookDecision.pass();
    }

    public static HookDecision<Void> onBlockOverlay() {
        return NoRender.shouldHideBlockOverlay()
                ? HookDecision.cancel()
                : HookDecision.pass();
    }

    public static Object filterMobEffectFogFunction(Object fogFunction) {
        if (fogFunction == null) {
            return null;
        }
        String className = fogFunction.getClass().getName();
        if (className.endsWith("$BlindnessFogFunction") && NoRender.shouldHideBlindness()) {
            return null;
        }
        if (className.endsWith("$DarknessFogFunction") && NoRender.shouldHideDarkness()) {
            return null;
        }
        return fogFunction;
    }
}
