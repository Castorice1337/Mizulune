/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldExpandTechnique.
 * Licensed under GNU GPL v3 or later.
 */
package shit.zen.modules.impl.movement.scaffold.v2.technique;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.rotation.Rotation;

public final class ExpandTechnique implements Technique {
    private final Settings settings;

    public ExpandTechnique() {
        this(Settings.DEFAULT);
    }

    public ExpandTechnique(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULT : settings;
    }

    @Override
    public String name() {
        return "Expand";
    }

    public Settings settings() {
        return this.settings;
    }

    @Override
    public List<TargetOffset> targetOffsets(TargetInput input) {
        double radians = Math.toRadians(input.playerYaw());
        List<TargetOffset> offsets = new ArrayList<>(this.settings.length() + 1);
        for (int expand = 0; expand <= this.settings.length(); expand++) {
            BlockPos offset = new BlockPos(
                    (int) (-Math.sin(radians) * expand),
                    0,
                    (int) (Math.cos(radians) * expand));
            offsets.add(new TargetOffset(
                    offset,
                    SearchOffsets.EXACT,
                    TargetPriority.POSITION,
                    AimMode.CENTER,
                    true));
        }
        return List.copyOf(offsets);
    }

    @Override
    public Rotation rotation(RotationInput input) {
        BlockPlacementTarget target = input.target();
        if (target == null || input.eyePosition() == null) {
            return null;
        }
        Vec3 blockCenter = Vec3.atCenterOf(target.placedBlockPos());
        return new Rotation(input.eyePosition(), blockCenter);
    }

    public record Settings(int length) {
        public static final Settings DEFAULT = new Settings(4);

        public Settings {
            if (length < 1 || length > 10) {
                throw new IllegalArgumentException("length must be in [1, 10]");
            }
        }
    }
}
