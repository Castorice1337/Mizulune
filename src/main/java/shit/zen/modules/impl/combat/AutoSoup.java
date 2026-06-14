package shit.zen.modules.impl.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import shit.zen.event.impl.SprintEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.NumberValue;
import shit.zen.utils.animation.Timer;
import shit.zen.utils.game.ItemUtil;
import shit.zen.utils.game.PlayerUtil;
import shit.zen.event.EventTarget;

public class AutoSoup
extends Module {
    public static AutoSoup INSTANCE;
    private final NumberValue health = new NumberValue("Health", 15, 0, 20, 1);
    private final NumberValue delay = new NumberValue("Delay", 300, 0, 1000, 1);
    private final NumberValue switchDelay = new NumberValue("Switch Delay", 100, 0, 1000, 1);
    private final BooleanValue drop = new BooleanValue("Drop", true);
    private final Timer switchDelayTimer = new Timer();
    private final Timer delayTimer = new Timer();
    private int prevSelectedSlot = -1;
    public boolean isUsingSoup;

    public AutoSoup() {
        super("AutoSoup", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    protected void onDisable() {
        this.prevSelectedSlot = -1;
        this.isUsingSoup = false;
    }

    @EventTarget
    public void onSprint(SprintEvent sprintEvent) {
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (this.switchDelayTimer.hasPassed(this.switchDelay.getValue().longValue()) && this.prevSelectedSlot != -1) {
            mc.player.getInventory().selected = this.prevSelectedSlot;
            PlayerUtil.sendCarriedItem();
            this.prevSelectedSlot = -1;
            this.delayTimer.reset();
        }
        if (!this.delayTimer.hasPassed(this.delay.getValue().longValue())) {
            return;
        }
        int soupSlot = ItemUtil.findItemInRange(0, 9, Items.MUSHROOM_STEW);
        if (mc.player.getHealth() <= this.health.getValue().floatValue() && soupSlot != -1) {
            boolean alreadySelected = mc.player.getInventory().selected == soupSlot;
            if (!alreadySelected) {
                this.prevSelectedSlot = mc.player.getInventory().selected;
                mc.player.getInventory().selected = soupSlot;
                PlayerUtil.sendCarriedItem();
                this.switchDelayTimer.reset();
            }
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            this.isUsingSoup = true;
            if (this.drop.getValue()) {
                mc.player.drop(true);
            }
            this.delayTimer.reset();
        } else {
            this.isUsingSoup = false;
        }
    }
}
