package shit.zen.gui.newclickgui;

import lombok.Getter;
import lombok.Generated;
import shit.zen.gui.newclickgui.CategoryPanel;
import shit.zen.gui.newclickgui.UIElement;
import shit.zen.settings.Setting;
import shit.zen.utils.animation.SmoothAnimationTimer;

public abstract class SettingElement<T extends Setting<?>>
extends UIElement {
    @Getter
    protected final CategoryPanel parentPanel;
    @Getter
    protected final T setting;
    @Getter
    protected final SmoothAnimationTimer visibilityTimer = new SmoothAnimationTimer();

    @Generated
    public SettingElement(CategoryPanel categoryPanel, T setting) {
        this.parentPanel = categoryPanel;
        this.setting = setting;
    }
}