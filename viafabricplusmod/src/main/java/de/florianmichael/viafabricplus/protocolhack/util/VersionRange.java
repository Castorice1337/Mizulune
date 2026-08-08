/*
 * This file is part of ViaFabricPlus - https://github.com/ViaVersion/ViaFabricPlus
 *
 * ViaLoader 2.2.13 changed its VersionRange to ProtocolVersion. ViaFabricPlus
 * 2.8 intentionally keeps VersionEnum because its UI, settings and legacy
 * protocol ordering all use that model.
 */
package de.florianmichael.viafabricplus.protocolhack.util;

import net.raphimc.vialoader.util.VersionEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VersionRange {
    private final VersionEnum min;
    private final VersionEnum max;
    private final List<VersionRange> ranges = new ArrayList<>();

    private VersionRange(final VersionEnum min, final VersionEnum max) {
        this.min = min;
        this.max = max;
    }

    public static VersionRange andNewer(final VersionEnum version) {
        return new VersionRange(version, null);
    }

    public static VersionRange single(final VersionEnum version) {
        return new VersionRange(version, version);
    }

    public static VersionRange andOlder(final VersionEnum version) {
        return new VersionRange(null, version);
    }

    public static VersionRange of(final VersionEnum min, final VersionEnum max) {
        return new VersionRange(min, max);
    }

    public static VersionRange all() {
        return new VersionRange(null, null);
    }

    public VersionRange add(final VersionRange range) {
        ranges.add(range);
        return this;
    }

    public boolean contains(final VersionEnum version) {
        if (ranges.stream().anyMatch(range -> range.contains(version))) {
            return true;
        }
        if (min == null && max == null) {
            return true;
        }
        if (min == null) {
            return version.isOlderThanOrEqualTo(max);
        }
        if (max == null) {
            return version.isNewerThanOrEqualTo(min);
        }
        return version.ordinal() >= min.ordinal() && version.ordinal() <= max.ordinal();
    }

    public VersionEnum getMin() {
        return min;
    }

    public VersionEnum getMax() {
        return max;
    }

    @Override
    public String toString() {
        if (min == null && max == null) {
            return "*";
        }
        final StringBuilder suffix = new StringBuilder();
        for (final VersionRange range : ranges) {
            suffix.append(", ").append(range);
        }
        if (min == null) {
            return "<= " + max.getName() + suffix;
        }
        if (max == null) {
            return ">= " + min.getName() + suffix;
        }
        if (Objects.equals(min, max)) {
            return min.getName() + suffix;
        }
        return min.getName() + " - " + max.getName() + suffix;
    }
}
