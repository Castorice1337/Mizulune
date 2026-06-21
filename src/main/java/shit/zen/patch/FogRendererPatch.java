package shit.zen.patch;

import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Transform;
import net.minecraft.client.renderer.FogRenderer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import shit.zen.modules.impl.render.NoRender;
import shit.zen.utils.misc.ReflectionUtil;

@Patch(FogRenderer.class)
public class FogRendererPatch {
    private static final String FOG_RENDERER = "net/minecraft/client/renderer/FogRenderer";
    private static final String FOG_FUNCTION = "net/minecraft/client/renderer/FogRenderer$MobEffectFogFunction";
    private static final String PRIORITY_DESC = "(Lnet/minecraft/world/entity/Entity;F)L" + FOG_FUNCTION + ";";

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

    @Transform(
            method = "setupColor",
            desc = "(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V"
    )
    public static void transformSetupColor(MethodNode methodNode) {
        filterPriorityFogCalls(methodNode);
    }

    @Transform(
            method = "setupFog",
            desc = "(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V"
    )
    public static void transformSetupFog(MethodNode methodNode) {
        filterPriorityFogCalls(methodNode);
    }

    private static void filterPriorityFogCalls(MethodNode methodNode) {
        String methodName = ReflectionUtil.getMappedMethodName(FogRenderer.class, "getPriorityFogFunction", PRIORITY_DESC);
        for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode methodInsn)) {
                continue;
            }
            if (!methodInsn.owner.equals(FOG_RENDERER)
                    || !methodInsn.name.equals(methodName)
                    || !methodInsn.desc.equals(PRIORITY_DESC)) {
                continue;
            }
            InsnList filter = new InsnList();
            filter.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(FogRendererPatch.class),
                    "filterMobEffectFogFunction",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    false));
            filter.add(new TypeInsnNode(Opcodes.CHECKCAST, FOG_FUNCTION));
            methodNode.instructions.insert(methodInsn, filter);
        }
    }
}
