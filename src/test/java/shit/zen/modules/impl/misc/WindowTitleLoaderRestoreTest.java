package shit.zen.modules.impl.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class WindowTitleLoaderRestoreTest {
    @Test
    void disablingCustomTitleDelegatesToTheActiveLoader() throws Exception {
        MethodNode disable = readClass(WindowTitle.class).methods.stream()
            .filter(method -> method.name.equals("onDisable"))
            .findFirst()
            .orElseThrow();

        assertTrue(Arrays.stream(disable.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .anyMatch(call -> call.owner.equals(Type.getInternalName(Minecraft.class))
                && call.name.equals("updateTitle") && call.desc.equals("()V")));
        assertFalse(Arrays.stream(disable.instructions.toArray())
            .filter(LdcInsnNode.class::isInstance)
            .map(LdcInsnNode.class::cast)
            .map(instruction -> instruction.cst)
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .anyMatch(value -> value.contains("Forge") || value.contains("Fabric")));
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resource)) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
