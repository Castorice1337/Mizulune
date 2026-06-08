package shit.zen.modules.impl.render;

import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.render.Renderer;
import shit.zen.render.backend.BackendType;
import shit.zen.settings.impl.ModeSetting;

public class Interface
extends Module {
    public final ModeSetting renderBackendSetting = new ModeSetting("Render Backend", "Skiko", "Legacy") {
        @Override
        public void onChanged(String previous, String current) {
            Renderer.setBackend(BackendType.fromProperty(current));
        }
    }.withDefault("Skiko");

    public Interface() {
        super("Interface", Category.RENDER);
    }
}
