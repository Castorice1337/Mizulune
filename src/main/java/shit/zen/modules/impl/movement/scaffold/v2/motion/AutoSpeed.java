/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ModuleScaffold AutoSpeed and ModuleSpeed.running:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a side-effect-free Scaffold v2 activation policy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.motion;

import java.util.Objects;

public final class AutoSpeed {
    public static final boolean DEFAULT_ENABLED = false;
    public static final Settings DEFAULTS = new Settings(DEFAULT_ENABLED);

    private AutoSpeed() {
    }

    public static boolean requestsSpeed(boolean scaffoldRunning, Settings settings) {
        Objects.requireNonNull(settings, "settings");
        return scaffoldRunning && settings.enabled();
    }

    public static boolean resolveActivation(
            boolean speedModuleRunning,
            boolean scaffoldRunning,
            Settings settings) {
        return speedModuleRunning || requestsSpeed(scaffoldRunning, settings);
    }

    public static boolean shouldRun(
            boolean speedModuleRunning,
            boolean scaffoldRunning,
            Settings settings,
            boolean downstreamRequirementsPass) {
        return resolveActivation(speedModuleRunning, scaffoldRunning, settings)
                && downstreamRequirementsPass;
    }

    public record Settings(boolean enabled) {
    }
}
