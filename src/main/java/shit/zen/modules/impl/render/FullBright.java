package shit.zen.modules.impl.render;

import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.settings.impl.NumberSetting;
import shit.zen.utils.misc.Triple;
import shit.zen.utils.misc.TripleProvider;

public class FullBright
extends Module
implements TripleProvider {
    public static FullBright INSTANCE;
    public final NumberSetting brightnessSetting = new NumberSetting("Brightness", 100.0f, 0.0f, 100.0f, 1.0f);

    public FullBright() {
        super("FullBright", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public Triple getTriple() {
        if (this.isEnabled()) {
            return new Triple(this.getName(), String.valueOf(this.brightnessSetting.getValue().intValue()), true);
        }
        return null;
    }
}