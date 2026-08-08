package shit.zen.patch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import asm.patchify.loader.PatchTransformer;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import shit.zen.ZenClient;
import shit.zen.hook.MinecraftHookCallbacks;
import shit.zen.modules.impl.world.Protocol;
import shit.zen.protocol.heypixel.HeyPixelProtocolRuntime;

final class MinecraftPatchProtocolLifecycleTest {
    @Test
    void protocolMainTickAndReadyEndPhaseAreIndependentOfGeneralReadiness() throws Exception {
        ClassNode patch = readClass(MinecraftPatch.class);
        MethodNode head = method(
            patch,
            "onTick",
            "(Lnet/minecraft/client/Minecraft;Lshit/zen/patch/CallbackInfo;)V");
        MethodNode tail = method(
            patch,
            "onTickPost",
            "(Lnet/minecraft/client/Minecraft;Lshit/zen/patch/CallbackInfo;)V");

        assertTrue(calls(head, MinecraftHookCallbacks.class, "onTickHead"));
        assertTrue(calls(tail, MinecraftHookCallbacks.class, "onTickTail"));

        ClassNode callbacks = readClass(MinecraftHookCallbacks.class);
        MethodNode sharedHead = method(
            callbacks,
            "onTickHead",
            "(Lnet/minecraft/client/Minecraft;)V");
        MethodNode sharedTail = method(callbacks, "onTickTail", "()V");
        assertTrue(calls(sharedHead, "tickProtocolBootstrap"));
        assertTrue(calls(sharedTail, "tickProtocolBootstrapEnd"));

        ClassNode protocol = readClass(Protocol.class);
        MethodNode bootstrapHead = method(protocol, "bootstrapTick", "()V");
        MethodNode bootstrapTail = method(protocol, "bootstrapTickEnd", "()V");
        assertTrue(calls(bootstrapHead, HeyPixelProtocolRuntime.class, "tickId3Phase"));
        assertTrue(calls(bootstrapTail, HeyPixelProtocolRuntime.class, "tickId3Phase"));
        assertTrue(calls(bootstrapTail, HeyPixelProtocolRuntime.class, "tickReadyPhase"));
    }

    @Test
    void clearLevelAndClientShutdownBothReachProtocolLogout() throws Exception {
        ClassNode patch = readClass(MinecraftPatch.class);
        MethodNode clearLevel = method(
            patch,
            "onClearLevel",
            "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/Screen;"
                + "Lshit/zen/patch/CallbackInfo;)V");
        assertTrue(calls(clearLevel, MinecraftHookCallbacks.class, "onClearLevel"));

        ClassNode callbacks = readClass(MinecraftHookCallbacks.class);
        MethodNode sharedClearLevel = method(
            callbacks,
            "onClearLevel",
            "(Lnet/minecraft/client/gui/screens/Screen;)V");
        assertTrue(calls(sharedClearLevel, Protocol.class, "onLoggingOut"));

        ClassNode client = readClass(ZenClient.class);
        MethodNode shutdown = method(client, "shutdown", "()V");
        assertTrue(calls(shutdown, Protocol.class, "onLoggingOut"));
        assertTrue(calls(shutdown, Protocol.class, "shutdownRuntime"));
    }

    @Test
    void transformedMinecraftClearLevelHookHasTheExactReceiverAndVerifies() throws Exception {
        String resource = Minecraft.class.getName().replace('.', '/') + ".class";
        ClassLoader loader = Minecraft.class.getClassLoader();
        byte[] original;
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            assertNotNull(stream);
            original = stream.readAllBytes();
        }

        ClassReader reader = new ClassReader(original);
        ClassNode minecraft = new ClassNode();
        reader.accept(minecraft, 0);
        PatchTransformer.apply(MinecraftPatch.class, minecraft);

        MethodNode clearLevel = method(
            minecraft,
            "clearLevel",
            "(Lnet/minecraft/client/gui/screens/Screen;)V");
        assertTrue(calls(clearLevel, MinecraftPatch.class, "onClearLevel"));

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        minecraft.accept(writer);
        StringWriter verificationOutput = new StringWriter();
        CheckClassAdapter.verify(
            new ClassReader(writer.toByteArray()),
            loader,
            false,
            new PrintWriter(verificationOutput)
        );
        assertTrue(
            verificationOutput.toString().isBlank(),
            () -> "Transformed Minecraft failed verification:\n" + verificationOutput
        );
    }

    private static boolean calls(MethodNode method, String name) {
        return calls(method, ZenClient.class, name);
    }

    private static boolean calls(MethodNode method, Class<?> owner, String name) {
        return Arrays.stream(method.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .anyMatch(invocation -> Type.getInternalName(owner).equals(invocation.owner)
                && name.equals(invocation.name));
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
            .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing method " + name + descriptor));
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
