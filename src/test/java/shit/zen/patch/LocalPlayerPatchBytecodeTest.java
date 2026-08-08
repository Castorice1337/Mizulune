package shit.zen.patch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import asm.patchify.loader.PatchTransformer;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import net.minecraft.client.player.LocalPlayer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

final class LocalPlayerPatchBytecodeTest {
    @Test
    void transformedLocalPlayerPassesAsmVerification() throws Exception {
        String resourceName = LocalPlayer.class.getName().replace('.', '/') + ".class";
        ClassLoader loader = LocalPlayer.class.getClassLoader();

        byte[] original;
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            assertNotNull(stream, "LocalPlayer bytecode must be available on the test classpath");
            original = stream.readAllBytes();
        }

        ClassReader reader = new ClassReader(original);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        PatchTransformer.apply(LocalPlayerPatch.class, classNode);

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);

        StringWriter verificationOutput = new StringWriter();
        CheckClassAdapter.verify(
                new ClassReader(writer.toByteArray()),
                loader,
                false,
                new PrintWriter(verificationOutput));
        assertTrue(
                verificationOutput.toString().isBlank(),
                () -> "Transformed LocalPlayer failed verification:\n" + verificationOutput);
    }
}
