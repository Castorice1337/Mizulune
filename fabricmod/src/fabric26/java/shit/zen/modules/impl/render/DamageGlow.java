package shit.zen.modules.impl.render;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.EntityHurtEvent;
import shit.zen.event.impl.EntityRemoveEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.MizuColor;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

/**
 * Keeps DamageGlow lifecycle/state on 26.2. Entity-model replay moved to render
 * states and cannot safely submit the old mutable model through Sodium/Iris;
 * snapshots are retained until the dedicated state renderer consumes them.
 */
public class DamageGlow extends Module {
    public record EntitySnapshot(long startTime, long expireTime, double x, double y, double z, int hurtTime) { }

    public static DamageGlow INSTANCE;
    private final Map<Integer, List<EntitySnapshot>> glowingEntities = new ConcurrentHashMap<>();
    private Value<MizuColor> glowColor;

    public DamageGlow() {
        super("DamageGlow", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        this.glowColor = root.group("glow", "Glow")
            .color("color", "Color", MizuColor.ofArgb(45, 0, 0, 0))
            .alias("Color").alias("Color R").alias("Color G").alias("Color B").alias("Alpha");
    }

    @Override
    public void onEnable() {
        this.glowingEntities.clear();
    }

    private void addGlowEffect(LivingEntity entity) {
        if (entity == null || entity.hurtTime <= 0) return;
        long now = System.currentTimeMillis();
        List<EntitySnapshot> list = this.glowingEntities.computeIfAbsent(entity.getId(),
            ignored -> new CopyOnWriteArrayList<>());
        cleanExpiredGlows(list);
        list.add(new EntitySnapshot(now, now + 1500L, entity.getX(), entity.getY(), entity.getZ(), entity.hurtTime));
        while (list.size() > 6) list.remove(0);
    }

    private void cleanExpiredGlows(List<EntitySnapshot> list) {
        long now = System.currentTimeMillis();
        list.removeIf(snapshot -> now > snapshot.expireTime());
    }

    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (event.entity() instanceof Player && event.entity() == mc.player) return;
        addGlowEffect(event.entity());
    }

    @EventTarget
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.dead() || event.entity() == mc.player) return;
        if (event.entity() instanceof LivingEntity living) addGlowEffect(living);
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        for (Map.Entry<Integer, List<EntitySnapshot>> entry : this.glowingEntities.entrySet()) {
            cleanExpiredGlows(entry.getValue());
        }
        this.glowingEntities.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
