/*
 * This file includes target classification ideas adapted from LiquidBounce Nextgen:
 * net.ccbluex.liquidbounce.utils.combat.CombatExtensions
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen as a shared client-level attack target settings surface.
 */
package shit.zen.modules.impl.combat;

import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;

public class TargetSettings extends Module {
    public static TargetSettings INSTANCE;

    public final BooleanValue attackPlayers = new BooleanValue("Attack Players", true);
    public final BooleanValue attackInvisible = new BooleanValue("Attack Invisible", false);
    public final BooleanValue attackAnimals = new BooleanValue("Attack Animals", false);
    public final BooleanValue attackMobs = new BooleanValue("Attack Mobs", true);

    public TargetSettings() {
        super("Targets", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup entities = root.group("entities", "Entities");
        entities.add(this.attackPlayers);
        entities.add(this.attackInvisible);
        entities.add(this.attackAnimals);
        entities.add(this.attackMobs);
    }

    @Override
    protected boolean defaultHiddenInModuleList() {
        return true;
    }
}
