package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Transform;
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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.ChatReceiveEvent;
import shit.zen.hud.TabListInfo;
import shit.zen.modules.impl.render.DynamicIsland;
import shit.zen.modules.impl.render.Watermark;
import shit.zen.utils.misc.ReflectionUtil;
import shit.zen.hook.PlayerTabOverlayHookCallbacks;

@Patch(PlayerTabOverlay.class)
public class PlayerTabOverlayPatch extends ClientBase {
    private static final ThreadLocal<Boolean> renderState = ThreadLocal.withInitial(() -> false);

    public static List<FormattedCharSequence> hookHeader(Font font, FormattedText text, int width) {
        return PlayerTabOverlayHookCallbacks.header(font, text, width);
    }

    public static List<FormattedCharSequence> hookFooter(Font font, FormattedText text, int width) {
        return PlayerTabOverlayHookCallbacks.footer(font, text, width);
    }

    public static Component hookName(PlayerTabOverlay overlay, PlayerInfo info) {
        return PlayerTabOverlayHookCallbacks.name(overlay, info);
    }

    @Transform(
            method = "render",
            desc = "(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V"
    )
    public static void transformRender(MethodNode methodNode) {
        InsnList instructions = methodNode.instructions;
        int splitCount = 0;
        for (AbstractInsnNode insn : instructions) {
            if (!(insn instanceof MethodInsnNode methodInsn)) continue;
            if (methodInsn.owner.equals("net/minecraft/client/gui/Font")
                    && methodInsn.name.equals(ReflectionUtil.getMappedMethodName(
                            Font.class,
                            "split",
                            "(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"))
                    && methodInsn.desc.equals("(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;")) {
                splitCount++;
                String hookName = splitCount == 1 ? "hookHeader" : (splitCount == 2 ? "hookFooter" : null);
                if (hookName == null) continue;
                MethodInsnNode replacement = new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PlayerTabOverlayPatch.class),
                        hookName,
                        "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
                        false);
                instructions.set(methodInsn, replacement);
            }
            if (methodInsn.owner.equals("net/minecraft/client/gui/components/PlayerTabOverlay")
                    && methodInsn.name.equals("getNameForDisplay")
                    && methodInsn.desc.equals("(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;")) {
                MethodInsnNode replacement = new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(PlayerTabOverlayPatch.class),
                        "hookName",
                        "(Lnet/minecraft/client/gui/components/PlayerTabOverlay;Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;",
                        false);
                instructions.set(methodInsn, replacement);
            }
        }
    }

    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(At.Type.HEAD)
    )
    public static void onRenderPre(PlayerTabOverlay overlay, GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo callbackInfo) {
        if (PlayerTabOverlayHookCallbacks.onRenderPre(overlay, graphics).handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "render",
            desc = "(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(At.Type.TAIL)
    )
    public static void onRenderPost(PlayerTabOverlay overlay, GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo callbackInfo) {
        PlayerTabOverlayHookCallbacks.onRenderPost(graphics);
    }
}
