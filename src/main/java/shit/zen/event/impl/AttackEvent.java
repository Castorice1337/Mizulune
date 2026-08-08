package shit.zen.event.impl;

import lombok.Getter;
import net.minecraft.world.entity.Entity;
import shit.zen.event.EventMarker;



public class AttackEvent implements EventMarker {
    @Getter
    private final Entity target;

    @Getter
    private final boolean post;

    public AttackEvent(Entity target,boolean post){
        this.target = target;
        this.post = post;
    }

}
