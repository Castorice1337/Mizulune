package shit.zen.asm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

final class MethodWrapperTest {
    @Test
    void invokesMethodInheritedFromSuperclass() throws Throwable {
        MethodWrapper wrapper = MethodWrapper.getInstance(
                Type.getInternalName(Child.class),
                "inheritedValue",
                "()I");

        assertEquals(42, wrapper.call(new Child()));
    }

    private static class Parent {
        public int inheritedValue() {
            return 42;
        }
    }

    private static final class Child extends Parent {
    }
}
