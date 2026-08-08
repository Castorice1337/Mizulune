package shit.zen.hook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.DisconnectEvent;
import shit.zen.event.impl.PostMotionEvent;
import shit.zen.event.impl.PreMotionEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.impl.movement.NoSlow;
import shit.zen.modules.impl.render.ESP;
import shit.zen.render.Renderer;

/** Shared Minecraft callbacks; platform adapters only translate cancellation and originals. */
public final class MinecraftHookCallbacks {
    private static HitResult savedHitResult;

    private MinecraftHookCallbacks() {
    }

    public static void onTickHead(Minecraft minecraft) {
        ensureClientInitialized(minecraft);
        ZenClient client = ZenClient.instance;
        if (client != null) {
            client.tickProtocolBootstrap();
        }
        if (ZenClient.isReady()) {
            ZenClient.serverTickRate = 1.0f;
            ClientBase.yaw = minecraft.player.getYRot();
            client.getEventBus().call(new TickEvent());
        }
    }

    public static void onTickTail() {
        ZenClient client = ZenClient.instance;
        if (client != null) {
            client.tickProtocolBootstrapEnd();
        }
        if (ZenClient.isReady()) {
            client.getEventBus().call(new PostMotionEvent());
        }
    }

    public static void onClose() {
        ZenClient client = ZenClient.instance;
        if (client != null) {
            client.shutdown();
        }
    }

    public static void onClearLevel(Screen screen) {
        ZenClient client = ZenClient.instance;
        if (client != null && client.getProtocolModule() != null) {
            client.getProtocolModule().onLoggingOut();
        }
    }

    public static void onSetLevel(ClientLevel level) {
        if (level == null && ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(new DisconnectEvent());
        }
    }

    public static HookDecision<Void> onHandleKeybinds() {
        if (!ZenClient.isReady()) return HookDecision.pass();
        PreMotionEvent event = new PreMotionEvent();
        ZenClient.getInstance().getEventBus().call(event);
        return event.isCancelled() ? HookDecision.cancel() : HookDecision.pass();
    }

    public static void onStartUseItemHead(Minecraft minecraft) {
        boolean redirectHit = NoSlow.isBlocking(minecraft);
        if (MultiPlayerGameModeHookCallbacks.isTraceEnabled()) {
            ClientBase.logger.info(
                "[InteractionTrace] startUseItem screen={} hit={} main={} offhand={} noSlowRedirect={}",
                minecraft.screen == null ? null : minecraft.screen.getClass().getSimpleName(),
                minecraft.hitResult == null ? null : minecraft.hitResult.getType(),
                minecraft.player == null ? null : minecraft.player.getMainHandItem(),
                minecraft.player == null ? null : minecraft.player.getOffhandItem(),
                redirectHit
            );
        }
        if (!redirectHit) return;
        savedHitResult = minecraft.hitResult;
        if (savedHitResult == null) return;
        Vec3 location = savedHitResult.getLocation();
        minecraft.hitResult = BlockHitResult.miss(
            location,
            Direction.DOWN,
            BlockPos.containing(location)
        );
    }

    public static void onStartUseItemTail(Minecraft minecraft) {
        if (savedHitResult == null) return;
        minecraft.hitResult = savedHitResult;
        savedHitResult = null;
        if (MultiPlayerGameModeHookCallbacks.isTraceEnabled()) {
            ClientBase.logger.info("[InteractionTrace] startUseItem restored hit result");
        }
    }

    public static boolean onShouldEntityGlow(
        Entity entity,
        OriginalCall<Boolean> original
    ) throws Exception {
        HookDecision<Boolean> decision = shouldEntityGlow(entity);
        return decision.handled() ? decision.value() : original.call();
    }

    public static HookDecision<Boolean> shouldEntityGlow(Entity entity) {
        if (ZenClient.isReady()
            && ZenClient.instance.getModuleManager() != null
            && ESP.INSTANCE != null
            && ESP.INSTANCE.isGlowing(entity)) {
            return HookDecision.handled(true);
        }
        return HookDecision.pass();
    }

    public static void onResizeDisplay(Minecraft minecraft) {
        Renderer.setGuiScaleVerified((float) minecraft.getWindow().getGuiScale());
        Renderer.resetWindowFramebufferBounds(
            minecraft.getWindow().getWidth(),
            minecraft.getWindow().getHeight()
        );
    }

    private static void ensureClientInitialized(Minecraft minecraft) {
        if (ZenClient.instance != null || minecraft == null || minecraft.options == null) return;
        synchronized (MinecraftHookCallbacks.class) {
            if (ZenClient.instance != null || minecraft.options == null) return;
            ClientBase.mc = minecraft;
            ClientBase.isLoading = true;
            ZenClient.bootstrap();
        }
    }
}
