package shit.zen.fabric.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.fantnel.ui.FantnelScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mizulune$addFantnelButton(CallbackInfo callback) {
        this.addRenderableWidget(Button.builder(Component.literal("FantNEL"), button ->
                this.minecraft.setScreen(new FantnelScreen((Screen) (Object) this)))
            .bounds(this.width - 104, this.height - 28, 100, 20)
            .build());
    }
}
