/*
 * This file is part of Mizulune/OpenZen.
 *
 * Adapted from LiquidBounce ScaffoldBlinkFeature:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 as a packet-agnostic Scaffold v2 queue/flush policy.
 */
package shit.zen.modules.impl.movement.scaffold.v2.motion;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class Blink {
    public static final int MIN_TIME_MILLIS = 0;
    public static final int MAX_TIME_MILLIS = 3000;
    public static final TimeRange DEFAULT_TIME = new TimeRange(50, 250);
    public static final Settings DEFAULTS = new Settings(false, DEFAULT_TIME, Set.of());

    private long pulseTimeMillis;
    private long lastResetMillis;

    public int onBlockPlacement(Settings settings) {
        return this.onBlockPlacement(settings, ThreadLocalRandom.current());
    }

    public int onBlockPlacement(Settings settings, RandomGenerator random) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(random, "random");
        int sampledTime = settings.time().sample(random);
        this.onBlockPlacement(sampledTime);
        return sampledTime;
    }

    public void onBlockPlacement(int sampledTimeMillis) {
        if (sampledTimeMillis < MIN_TIME_MILLIS || sampledTimeMillis > MAX_TIME_MILLIS) {
            throw new IllegalArgumentException("sampledTimeMillis must be in 0..3000");
        }
        this.pulseTimeMillis = sampledTimeMillis;
    }

    public Decision decide(PacketContext packet, Settings settings, long nowMillis) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(settings, "settings");

        if (!settings.enabled()) {
            return new Decision(Action.FLUSH, Reason.DISABLED, Set.of());
        }
        if (!packet.outgoing()) {
            return new Decision(Action.FLUSH, Reason.NON_OUTGOING, Set.of());
        }

        boolean timeElapsed = this.hasElapsed(nowMillis);
        EnumSet<FlushOn> matchingTriggers = matchingTriggers(packet, settings.flushOn());
        if (timeElapsed || !matchingTriggers.isEmpty()) {
            this.lastResetMillis = nowMillis;
            return new Decision(
                    Action.FLUSH,
                    timeElapsed ? Reason.TIME : Reason.CONDITION,
                    matchingTriggers);
        }

        if (!packet.onGround() || !this.hasElapsed(nowMillis)) {
            return new Decision(Action.QUEUE, Reason.NONE, Set.of());
        }
        return new Decision(Action.FLUSH, Reason.TIME, Set.of());
    }

    public long pulseTimeMillis() {
        return this.pulseTimeMillis;
    }

    public long lastResetMillis() {
        return this.lastResetMillis;
    }

    private boolean hasElapsed(long nowMillis) {
        return this.lastResetMillis + this.pulseTimeMillis < nowMillis;
    }

    private static EnumSet<FlushOn> matchingTriggers(PacketContext packet, Set<FlushOn> flushOn) {
        EnumSet<FlushOn> matches = EnumSet.noneOf(FlushOn.class);
        for (FlushOn condition : flushOn) {
            if (condition.matches(packet)) {
                matches.add(condition);
            }
        }
        return matches;
    }

    public record Settings(boolean enabled, TimeRange time, Set<FlushOn> flushOn) {
        public Settings {
            Objects.requireNonNull(time, "time");
            flushOn = flushOn == null || flushOn.isEmpty() ? Set.of() : Set.copyOf(flushOn);
        }
    }

    public record TimeRange(int minimum, int maximum) {
        public TimeRange {
            if (minimum < MIN_TIME_MILLIS
                    || maximum > MAX_TIME_MILLIS
                    || minimum > maximum) {
                throw new IllegalArgumentException("time range must be ordered within 0..3000");
            }
        }

        public int sample(RandomGenerator random) {
            Objects.requireNonNull(random, "random");
            return this.minimum == this.maximum
                    ? this.minimum
                    : random.nextInt(this.minimum, this.maximum + 1);
        }
    }

    public record PacketContext(
            boolean outgoing,
            boolean placePacket,
            boolean towering,
            boolean sneaking,
            boolean onGround) {
    }

    public record Decision(Action action, Reason reason, Set<FlushOn> matchingTriggers) {
        public Decision {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(reason, "reason");
            matchingTriggers = matchingTriggers == null || matchingTriggers.isEmpty()
                    ? Set.of()
                    : Set.copyOf(matchingTriggers);
        }
    }

    public enum Action {
        FLUSH,
        QUEUE
    }

    public enum Reason {
        NONE,
        DISABLED,
        NON_OUTGOING,
        TIME,
        CONDITION
    }

    public enum FlushOn {
        PLACE {
            @Override
            boolean matches(PacketContext packet) {
                return packet.placePacket();
            }
        },
        TOWERING {
            @Override
            boolean matches(PacketContext packet) {
                return packet.towering();
            }
        },
        SNEAKING {
            @Override
            boolean matches(PacketContext packet) {
                return packet.sneaking();
            }
        },
        NOT_SNEAKING {
            @Override
            boolean matches(PacketContext packet) {
                return !packet.sneaking();
            }
        },
        ON_GROUND {
            @Override
            boolean matches(PacketContext packet) {
                return packet.onGround();
            }
        },
        IN_AIR {
            @Override
            boolean matches(PacketContext packet) {
                return !packet.onGround();
            }
        };

        abstract boolean matches(PacketContext packet);
    }
}
