package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import shit.zen.modules.impl.render.XRay;

/**
 * "True XRay" terrain patch.
 *
 * <p>{@code Block.shouldRenderFace} is the vanilla face-culling decision evaluated for every block
 * face while chunk meshes are built. We hook it at HEAD: when the {@link XRay} module is enabled we
 * force the result so that only XRay target blocks render (all faces, hence visible through walls)
 * and every other block renders no faces at all (it becomes invisible). When XRay is disabled we
 * fall through untouched, leaving vanilla culling intact.
 *
 * <p>Toggling the module calls {@code mc.levelRenderer.allChanged()} (see {@link XRay#onEnable()}/
 * {@link XRay#onDisable()}), which rebuilds every chunk mesh so this decision is re-evaluated and
 * the effect applies/clears immediately.
 *
 * <p>The mojmap {@code method}/{@code desc} below are remapped to the runtime SRG name
 * ({@code m_152444_}) by {@code Bootstrap.remapMethod}, exactly like the other patches.
 */
@Patch(Block.class)
public class BlockPatch {
    @Inject(
            method = "shouldRenderFace",
            desc = "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(At.Type.HEAD)
    )
    public static void onShouldRenderFace(BlockState state, BlockGetter level, BlockPos pos,
                                          Direction direction, BlockPos neighborPos,
                                          CallbackInfo callbackInfo) {
        XRay xray = XRay.INSTANCE;
        if (xray == null || !xray.isEnabled()) {
            return; // module off -> keep vanilla face culling, game renders normally
        }
        callbackInfo.result = xray.isXrayVisible(state.getBlock()) ? Boolean.TRUE : Boolean.FALSE;
        callbackInfo.cancel();
    }
}
