package shit.zen.modules.impl.movement;

import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.impl.BooleanValue;

public class NoDelay
extends Module {
    public static NoDelay INSTANCE;
    public final BooleanValue fastDig = new BooleanValue("No Jump Delay", true);

    public NoDelay() {
        super("NoDelay", Category.MOVEMENT);
        INSTANCE = this;
    }
}
