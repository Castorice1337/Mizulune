package shit.zen.modules.impl.movement;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.StuckInBlockEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.event.EventTarget;

public class FastWeb
extends Module {
    private int lastWebTick = 0;
    private int webCount = 0;
    public FastWeb() {
        super("FastWeb", Category.MOVEMENT);
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (motionEvent.isPre() && mc.player != null && this.lastWebTick < mc.player.tickCount) {
            this.webCount = 0;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent strafeEvent) {
        if (this.webCount > 1) {
            strafeEvent.setSprinting(false);
        }
    }

    @EventTarget
    public void onStuckInBlock(StuckInBlockEvent stuckInBlockEvent) {
        if (stuckInBlockEvent.getBlockState().getBlock() == Blocks.COBWEB && mc.player != null) {
            this.lastWebTick = mc.player.tickCount;
            ++this.webCount;
            if (this.webCount > 5) {
                Vec3 vec3 = new Vec3(0.88, 1.88, 0.88);
                stuckInBlockEvent.setMotion(vec3);
            }
        }
    }
}