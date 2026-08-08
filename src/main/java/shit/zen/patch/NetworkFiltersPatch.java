package shit.zen.patch;

import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Transform;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraftforge.network.filters.NetworkFilters;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Makes Forge's login filter injection idempotent across ViaForge server transfers. */
@Patch(NetworkFilters.class)
public final class NetworkFiltersPatch {
    private static final String VANILLA_FILTER = "forge:vanilla_filter";

    private NetworkFiltersPatch() {
    }

    public static boolean isVanillaFilterInstalled(Connection connection) {
        if (connection == null) return false;
        Channel channel = connection.channel();
        if (channel == null) return false;
        ChannelPipeline pipeline = channel.pipeline();
        return pipeline != null && pipeline.get(VANILLA_FILTER) != null;
    }

    @Transform(method = "injectIfNecessary", desc = "(Lnet/minecraft/network/Connection;)V")
    public static void transformInjectIfNecessary(MethodNode methodNode) {
        InsnList header = new InsnList();
        header.add(new VarInsnNode(Opcodes.ALOAD, 0));
        header.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Type.getInternalName(NetworkFiltersPatch.class),
            "isVanillaFilterInstalled",
            "(Lnet/minecraft/network/Connection;)Z",
            false
        ));
        LabelNode continueInjection = new LabelNode();
        header.add(new JumpInsnNode(Opcodes.IFEQ, continueInjection));
        header.add(new InsnNode(Opcodes.RETURN));
        header.add(continueInjection);
        methodNode.instructions.insert(header);
    }
}
