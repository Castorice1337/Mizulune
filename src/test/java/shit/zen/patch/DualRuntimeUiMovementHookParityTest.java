package shit.zen.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import shit.zen.hook.ChatScreenHookCallbacks;
import shit.zen.hook.EntityHookCallbacks;

final class DualRuntimeUiMovementHookParityTest {
    @Test
    void patchifyAdaptersDelegateToSharedCallbacks() throws Exception {
        MethodNode chatRender = findMethod(readClass(ChatScreenPatch.class), "onRender");
        MethodNode chatClick = findMethod(readClass(ChatScreenPatch.class), "onMouseClicked");
        MethodNode moveRelative = findMethod(readClass(EntityPatch.class), "overwriteMoveRelative");

        assertTrue(calls(chatRender, ChatScreenHookCallbacks.class, "onRender"));
        assertTrue(calls(chatClick, ChatScreenHookCallbacks.class, "onMouseClicked"));
        assertTrue(calls(moveRelative, EntityHookCallbacks.class, "moveRelative"));
    }

    @Test
    void fabricRegistersEquivalentChatAndMovementAdapters() throws Exception {
        Path resources = Path.of("fabricmod", "src", "main", "resources");
        String mixinConfig = Files.readString(
                resources.resolve("mizulune.fabric.mixins.json"), StandardCharsets.UTF_8);
        assertTrue(mixinConfig.contains("\"ChatScreenMixin\""));
        assertTrue(mixinConfig.contains("\"EntityMixin\""));

        String chatMixin = Files.readString(resolveMixinSource("ChatScreenMixin"), StandardCharsets.UTF_8);
        String entityMixin = Files.readString(resolveMixinSource("EntityMixin"), StandardCharsets.UTF_8);
        assertTrue(chatMixin.contains("ChatScreenHookCallbacks.onRender"));
        assertTrue(chatMixin.contains("ChatScreenHookCallbacks.onMouseClicked"));
        assertTrue(entityMixin.contains("EntityHookCallbacks.moveRelative"));
    }

    @Test
    void preMotionHookUsesTheUnconditionalMethodHeadInBothRuntimes() throws Exception {
        Method asmHook = MinecraftPatch.class.getDeclaredMethod(
                "onHandleKeybinds",
                net.minecraft.client.Minecraft.class,
                CallbackInfo.class);
        Inject injection = asmHook.getAnnotation(Inject.class);
        assertNotNull(injection);
        assertEquals(At.Type.HEAD, injection.at().value());

        Path mixin = resolveMixinSource("MinecraftMixin");
        String source = Files.readString(mixin, StandardCharsets.UTF_8);
        assertTrue(source.contains("method = \"handleKeybinds\""));
        assertTrue(source.contains("at = @At(\"HEAD\")"));
    }

    private static boolean calls(MethodNode method, Class<?> owner, String name) {
        return Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .anyMatch(invocation -> Type.getInternalName(owner).equals(invocation.owner)
                        && name.equals(invocation.name));
    }

    private static MethodNode findMethod(ClassNode classNode, String name) {
        return classNode.methods.stream()
                .filter(method -> name.equals(method.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + name));
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        String resourceName = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(stream, "class bytecode must be available: " + type.getName());
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static Path resolveMixinSource(String mixin) {
        Path fabric26 = Path.of(
                "fabricmod", "src", "fabric26", "java", "shit", "zen", "fabric", "mixin",
                mixin + ".java");
        if (Files.isRegularFile(fabric26)) {
            return fabric26;
        }
        return Path.of(
                "fabricmod", "src", "main", "java", "shit", "zen", "fabric", "mixin",
                mixin + ".java");
    }
}
