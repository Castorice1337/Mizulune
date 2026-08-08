package shit.zen.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ModuleStableIdTest {
    @Test
    void moduleIdComesFromDisplayNameByDefault() {
        TestModule module = new TestModule("Dynamic Island");

        assertEquals("dynamic_island", module.getId());
    }

    @Test
    void moduleCanUseExplicitStableId() {
        ExplicitIdModule module = new ExplicitIdModule();

        assertEquals("target_hud", module.getId());
        assertEquals("TargetHUD", module.getName());
    }

    private static final class TestModule extends Module {
        private TestModule(String name) {
            super(name, Category.RENDER);
        }
    }

    private static final class ExplicitIdModule extends Module {
        private ExplicitIdModule() {
            super("target_hud", "TargetHUD", Category.RENDER);
        }
    }
}
