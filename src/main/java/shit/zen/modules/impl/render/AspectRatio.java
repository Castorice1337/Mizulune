package shit.zen.modules.impl.render;

import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.NumberSetting;

public class AspectRatio
extends Module {
    public static AspectRatio INSTANCE;
    public final NumberSetting ratioSetting = new NumberSetting("Ratio", 1.78f, 0.1f, 5.0f, 0.1f);

    public AspectRatio() {
        super("AspectRatio", Category.RENDER);
        INSTANCE = this;
    }
}