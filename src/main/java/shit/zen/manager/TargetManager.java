/*
 * This file includes target classification ideas adapted from LiquidBounce Nextgen:
 * net.ccbluex.liquidbounce.utils.combat.CombatExtensions
 * net.ccbluex.liquidbounce.utils.combat.TargetTracker
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen as a client-level attack target manager.
 */
package shit.zen.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.Generated;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.GameTickEvent;
import shit.zen.modules.impl.combat.AntiBots;
import shit.zen.modules.impl.combat.TargetSettings;
import shit.zen.modules.impl.world.Teams;
import shit.zen.utils.game.RotationUtil;

public class TargetManager
extends ClientBase {
    public static TargetManager INSTANCE;
    private final List<LivingEntity> targets = new ArrayList<>();

    public TargetManager() {
        INSTANCE = this;
    }

    @EventTarget
    public void onGameTick(GameTickEvent gameTickEvent) {
        if (mc.level == null || !ZenClient.isReady() || TargetSettings.INSTANCE == null) {
            this.targets.clear();
            return;
        }
        this.targets.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity)) continue;
            if (!this.isValidTarget(entity)) continue;
            this.targets.add(livingEntity);
        }
        this.targets.sort(Comparator.comparingDouble(this::distanceToPlayerBox));
    }

    public boolean isValidTarget(Entity entity) {
        if (entity == null || mc.player == null || mc.level == null || !ZenClient.isReady()) {
            return false;
        }
        if (entity == mc.player || !(entity instanceof LivingEntity livingEntity)) {
            return false;
        }
        TargetSettings settings = TargetSettings.INSTANCE;
        if (settings == null) {
            return false;
        }
        if (entity.isRemoved() || !livingEntity.isAlive() || livingEntity.isDeadOrDying() || livingEntity.getHealth() <= 0.0f) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return false;
        }
        if (entity.isInvisible() && !settings.attackInvisible.getValue()) {
            return false;
        }
        AntiBots antiBots = AntiBots.INSTANCE;
        if (antiBots != null && antiBots.isEnabled() && (AntiBots.isBot(entity) || AntiBots.isBedWarsBot(entity))) {
            return false;
        }
        if (Teams.instance != null && Teams.isSameTeam(entity)) {
            return false;
        }
        if (entity instanceof Player player) {
            if (player.isSpectator() || player.isSleeping() || entity.getBbWidth() < 0.5) {
                return false;
            }
            return settings.attackPlayers.getValue();
        }
        if (entity instanceof Animal || entity instanceof Squid || entity instanceof Villager) {
            return settings.attackAnimals.getValue();
        }
        if (entity instanceof Mob || entity instanceof Slime || entity instanceof Bat || entity instanceof AbstractGolem) {
            return settings.attackMobs.getValue();
        }
        return false;
    }

    public Stream<LivingEntity> getTargetsStream(float range) {
        if (mc.player == null) {
            return Stream.empty();
        }
        return new ArrayList<>(this.targets).stream()
                .filter(livingEntity -> this.distanceToPlayerBox(livingEntity) <= (double) range)
                .sorted(Comparator.comparingDouble(this::distanceToPlayerBox));
    }

    public List<LivingEntity> getTargets(float range) {
        return this.getTargetsStream(range).toList();
    }

    public List<LivingEntity> getTargets() {
        return new ArrayList<>(this.targets);
    }

    private double distanceToPlayerBox(LivingEntity livingEntity) {
        if (mc.player == null || livingEntity == null) {
            return Double.MAX_VALUE;
        }
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 closest = RotationUtil.closestPoint(eyes, livingEntity.getBoundingBox());
        return closest.distanceTo(eyes);
    }

    @Generated
    public List<LivingEntity> getTargetList() {
        return this.targets;
    }
}
