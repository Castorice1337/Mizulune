package asm.patchify.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

final class ResourceHierarchyClassWriterTest {
    @Test
    void resolvesClassesAndInterfacesWithoutDefiningThem() throws Exception {
        ResourceHierarchyClassWriter writer = writerFor(Child.class);

        assertEquals(
                Type.getInternalName(Parent.class),
                writer.getCommonSuperClass(
                        Type.getInternalName(Child.class),
                        Type.getInternalName(Parent.class)));
        assertEquals(
                Type.getInternalName(Marker.class),
                writer.getCommonSuperClass(
                        Type.getInternalName(Marker.class),
                        Type.getInternalName(MarkerChild.class)));
        assertEquals(
                "java/lang/Object",
                writer.getCommonSuperClass(
                        Type.getInternalName(Child.class),
                        Type.getInternalName(Unrelated.class)));
    }

    @Test
    void preservesReferenceArrayDimensions() throws Exception {
        ResourceHierarchyClassWriter writer = writerFor(Child.class);

        assertEquals(
                "[L" + Type.getInternalName(Parent.class) + ";",
                writer.getCommonSuperClass(
                        "[L" + Type.getInternalName(Child.class) + ";",
                        "[L" + Type.getInternalName(Parent.class) + ";"));
    }

    private static ResourceHierarchyClassWriter writerFor(Class<?> type) throws Exception {
        String resourceName = Type.getInternalName(type) + ".class";
        ClassLoader loader = type.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test class resource " + resourceName);
            }
            return new ResourceHierarchyClassWriter(
                    new ClassReader(stream),
                    ClassWriter.COMPUTE_FRAMES,
                    loader);
        }
    }

    private interface Marker {
    }

    private static class Parent {
    }

    private static final class Child extends Parent {
    }

    private static final class MarkerChild implements Marker {
    }

    private static final class Unrelated {
    }
}
