package shit.zen.hook;

import net.minecraft.world.level.block.state.BlockState;
import shit.zen.modules.impl.render.XRay;

/** Shared XRay face-culling decision for Patchify and Mixin. */
public final class BlockHookCallbacks {
    private BlockHookCallbacks() {
    }

    public static HookDecision<Boolean> shouldRenderFace(BlockState state) {
        XRay xray = XRay.INSTANCE;
        return xray != null && xray.isEnabled()
                ? HookDecision.handled(xray.isXrayVisible(state.getBlock()))
                : HookDecision.pass();
    }
}
