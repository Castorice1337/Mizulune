package shit.zen.modules.impl.render;

import shit.zen.modules.Category;
import shit.zen.modules.Module;

public class ItemTags extends Module {
    public static ItemTags INSTANCE;

    public ItemTags() {
        super("ItemTags", Category.RENDER);
        INSTANCE = this;
    }
}
