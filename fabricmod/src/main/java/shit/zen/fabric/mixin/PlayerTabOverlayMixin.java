package shit.zen.fabric.mixin;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.PlayerTabOverlayHookCallbacks;

/** Fabric adapter for tab-list NameProtect, Dynamic Island and Watermark layout. */
@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
                    ordinal = 0))
    private List<FormattedCharSequence> mizulune$header(Font font, FormattedText text, int width) {
        return PlayerTabOverlayHookCallbacks.header(font, text, width);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
                    ordinal = 1))
    private List<FormattedCharSequence> mizulune$footer(Font font, FormattedText text, int width) {
        return PlayerTabOverlayHookCallbacks.footer(font, text, width);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
    private Component mizulune$name(PlayerTabOverlay overlay, PlayerInfo info) {
        return PlayerTabOverlayHookCallbacks.name(overlay, info);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mizulune$renderHead(
            GuiGraphics graphics,
            int width,
            Scoreboard scoreboard,
            Objective objective,
            CallbackInfo callbackInfo) {
        if (PlayerTabOverlayHookCallbacks.onRenderPre(
                (PlayerTabOverlay) (Object) this, graphics).handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mizulune$renderTail(
            GuiGraphics graphics,
            int width,
            Scoreboard scoreboard,
            Objective objective,
            CallbackInfo callbackInfo) {
        PlayerTabOverlayHookCallbacks.onRenderPost(graphics);
    }
}
