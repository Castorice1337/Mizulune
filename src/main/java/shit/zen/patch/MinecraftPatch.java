package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import shit.zen.asm.Invocation;
import shit.zen.hook.HookDecision;
import shit.zen.hook.MinecraftHookCallbacks;

@Patch(Minecraft.class)
public class MinecraftPatch {
    public static volatile boolean initialized = false;

    public static void markInitialized() {
        initialized = true;
    }

    @Inject(method = "tick", desc = "()V")
    public static void onTick(Minecraft minecraft, CallbackInfo callbackInfo) throws Throwable {
        if (!initialized) {
            synchronized (MinecraftPatch.class) {
                if (!initialized) {
                    ModList.get().getMods().removeIf(modInfo -> modInfo.getModId().equals("hey"));
                    List<IModFileInfo> toRemove = new ArrayList<>();
                    for (IModFileInfo modFile : ModList.get().getModFiles()) {
                        for (IModInfo modInfo : modFile.getMods()) {
                            if (modInfo.getModId().equals("hey")) {
                                toRemove.add(modFile);
                            }
                        }
                    }
                    ModList.get().getModFiles().removeAll(toRemove);
                    initialized = true;
                }
            }
        }
        MinecraftHookCallbacks.onTickHead(minecraft);
    }

    @Inject(method = "tick", desc = "()V", at = @At(At.Type.TAIL))
    public static void onTickPost(Minecraft minecraft, CallbackInfo callbackInfo) throws Throwable {
        MinecraftHookCallbacks.onTickTail();
    }

    @Inject(method = "close", desc = "()V", at = @At(At.Type.HEAD))
    public static void onClose(Minecraft minecraft, CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onClose();
    }

    /** Exact vanilla/Forge logout boundary used by both disconnect and graceful JVM shutdown. */
    @Inject(
            method = "clearLevel",
            desc = "(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(At.Type.HEAD)
    )
    public static void onClearLevel(Minecraft minecraft, Screen screen, CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onClearLevel(screen);
    }

    @Inject(method = "setLevel", desc = "(Lnet/minecraft/client/multiplayer/ClientLevel;)V")
    public static void onSetLevel(
            Minecraft minecraft,
            ClientLevel level,
            CallbackInfo callbackInfo
    ) {
        MinecraftHookCallbacks.onSetLevel(level);
    }

    @Inject(
            method = "handleKeybinds",
            desc = "()V",
            at = @At(At.Type.HEAD)
    )
    public static void onHandleKeybinds(Minecraft minecraft, CallbackInfo callbackInfo) {
        HookDecision<Void> decision = MinecraftHookCallbacks.onHandleKeybinds();
        if (decision.handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "startUseItem", desc = "()V", at = @At(At.Type.HEAD))
    public static void onStartUseItemPre(Minecraft minecraft, CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onStartUseItemHead(minecraft);
    }

    @Inject(method = "startUseItem", desc = "()V", at = @At(At.Type.TAIL))
    public static void onStartUseItemPost(Minecraft minecraft, CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onStartUseItemTail(minecraft);
    }

    @WrapInvoke(
            method = "shouldEntityAppearGlowing",
            desc = "(Lnet/minecraft/world/entity/Entity;)Z",
            target = "net/minecraft/world/entity/Entity/isCurrentlyGlowing",
            targetDesc = "()Z"
    )
    public static boolean onShouldEntityGlow(Minecraft minecraft, Entity entity, Invocation<Entity, Boolean> original) throws Exception {
        return MinecraftHookCallbacks.onShouldEntityGlow(entity, original::call);
    }

    @Inject(method = "resizeDisplay", desc = "()V", at = @At(At.Type.TAIL))
    public static void onResizeDisplay(Minecraft minecraft, CallbackInfo callbackInfo) {
        MinecraftHookCallbacks.onResizeDisplay(minecraft);
    }
}
