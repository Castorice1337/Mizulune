package asm.patchify.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Computes stack-map frame hierarchies from class resources without defining classes.
 * Forge can invoke the transformer while a related Minecraft class is still being defined;
 * using {@link Class#forName(String)} there can recursively load the same hierarchy and throw
 * {@link ClassCircularityError}.
 */
final class ResourceHierarchyClassWriter extends ClassWriter {
    private static final String OBJECT = "java/lang/Object";
    private static final String CLONEABLE = "java/lang/Cloneable";
    private static final String SERIALIZABLE = "java/io/Serializable";

    private final ClassLoader loader;
    private final Map<String, ClassInfo> hierarchy = new HashMap<>();

    ResourceHierarchyClassWriter(ClassReader reader, int flags, ClassLoader loader) {
        super(reader, flags);
        this.loader = loader;
        this.hierarchy.put(reader.getClassName(), ClassInfo.from(reader));
    }

    @Override
    protected String getCommonSuperClass(String first, String second) {
        if (first.equals(second)) {
            return first;
        }
        if (first.startsWith("[") || second.startsWith("[")) {
            return this.getCommonArrayType(first, second);
        }
        if (this.isAssignableFrom(first, second)) {
            return first;
        }
        if (this.isAssignableFrom(second, first)) {
            return second;
        }

        ClassInfo firstInfo = this.getInfo(first);
        ClassInfo secondInfo = this.getInfo(second);
        if (!firstInfo.found() || !secondInfo.found()
                || firstInfo.isInterface() || secondInfo.isInterface()) {
            return OBJECT;
        }

        String current = firstInfo.superName();
        while (current != null) {
            if (this.isAssignableFrom(current, second)) {
                return current;
            }
            ClassInfo currentInfo = this.getInfo(current);
            if (!currentInfo.found()) {
                break;
            }
            current = currentInfo.superName();
        }
        return OBJECT;
    }

    private String getCommonArrayType(String first, String second) {
        if (!first.startsWith("[") || !second.startsWith("[")) {
            String nonArray = first.startsWith("[") ? second : first;
            return nonArray.equals(OBJECT) || nonArray.equals(CLONEABLE) || nonArray.equals(SERIALIZABLE)
                    ? nonArray
                    : OBJECT;
        }

        Type firstType = Type.getType(first);
        Type secondType = Type.getType(second);
        if (firstType.getDimensions() != secondType.getDimensions()) {
            return OBJECT;
        }
        Type firstElement = firstType.getElementType();
        Type secondElement = secondType.getElementType();
        if (firstElement.getSort() != Type.OBJECT || secondElement.getSort() != Type.OBJECT) {
            return OBJECT;
        }

        String commonElement = this.getCommonSuperClass(
                firstElement.getInternalName(),
                secondElement.getInternalName());
        return "[".repeat(firstType.getDimensions()) + "L" + commonElement + ";";
    }

    private boolean isAssignableFrom(String target, String candidate) {
        if (target.equals(candidate) || target.equals(OBJECT)) {
            return true;
        }
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(candidate);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (target.equals(current)) {
                return true;
            }
            ClassInfo info = this.getInfo(current);
            if (!info.found()) {
                continue;
            }
            if (info.superName() != null) {
                pending.addLast(info.superName());
            }
            pending.addAll(info.interfaces());
        }
        return false;
    }

    private ClassInfo getInfo(String internalName) {
        return this.hierarchy.computeIfAbsent(internalName, this::readInfo);
    }

    private ClassInfo readInfo(String internalName) {
        String resourceName = internalName + ".class";
        try (InputStream stream = this.openResource(resourceName)) {
            return stream == null ? ClassInfo.missing() : ClassInfo.from(new ClassReader(stream));
        } catch (IOException ignored) {
            return ClassInfo.missing();
        }
    }

    private InputStream openResource(String resourceName) {
        InputStream stream = this.loader == null ? null : this.loader.getResourceAsStream(resourceName);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (stream == null && contextLoader != null && contextLoader != this.loader) {
            stream = contextLoader.getResourceAsStream(resourceName);
        }
        return stream == null ? ClassLoader.getSystemResourceAsStream(resourceName) : stream;
    }

    private record ClassInfo(
            boolean found,
            int access,
            String superName,
            List<String> interfaces) {
        private static ClassInfo from(ClassReader reader) {
            return new ClassInfo(
                    true,
                    reader.getAccess(),
                    reader.getSuperName(),
                    List.of(reader.getInterfaces()));
        }

        private static ClassInfo missing() {
            return new ClassInfo(false, 0, null, List.of());
        }

        private boolean isInterface() {
            return (this.access & Opcodes.ACC_INTERFACE) != 0;
        }
    }
}
