/*
 * This file is part of Mizulune/OpenZen.
 *
 * Portions are adapted from LiquidBounce:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 scaffold pipeline.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class ScaffoldGeometry {
    private static final double NORMALIZED_TOLERANCE = 1.0E-4;

    private ScaffoldGeometry() {
    }

    static boolean isLikelyZero(Vec3 vector) {
        return vector == null || Mth.equal(vector.lengthSqr(), 0.0);
    }

    static Vec3 normalizeIfNeeded(Vec3 vector) {
        return Math.abs(vector.lengthSqr() - 1.0) < NORMALIZED_TOLERANCE
                ? vector
                : vector.normalize();
    }

    static Vec3 nearestPoint(AABB box, Vec3 point) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    static class Line {
        final Vec3 position;
        final Vec3 direction;

        Line(Vec3 position, Vec3 direction) {
            if (position == null || isLikelyZero(direction)) {
                throw new IllegalArgumentException("Line direction must not be zero");
            }
            this.position = position;
            this.direction = direction;
        }

        Vec3 nearestPointTo(Vec3 point) {
            NormalizedPlane plane = new NormalizedPlane(point, this.direction);
            Vec3 intersection = plane.intersection(this);
            return intersection == null ? point : intersection;
        }

        double distanceToSqr(Vec3 point) {
            return this.nearestPointTo(point).distanceToSqr(point);
        }

        double distanceToSqr(AABB box) {
            Vec3 pointOnLine = this.nearestPointTo(box);
            Vec3 pointOnBox = nearestPoint(box, pointOnLine);
            return pointOnLine.distanceToSqr(pointOnBox);
        }

        Vec3 getPositionChecked(double phi) {
            return this.getPosition(phi);
        }

        Vec3 getPosition(double phi) {
            return this.position.add(this.direction.scale(phi));
        }

        double getPhiForPoint(Vec3 point) {
            Vec3 fromPosition = point.subtract(this.position);
            double[][] coordinates = {
                    {fromPosition.x, this.direction.x},
                    {fromPosition.y, this.direction.y},
                    {fromPosition.z, this.direction.z}
            };
            double directionSum = 0.0;
            int count = 0;
            for (double[] coordinate : coordinates) {
                if (!Mth.equal(coordinate[1], 0.0)) {
                    directionSum += coordinate[1];
                    count++;
                }
            }
            if (count == 0) {
                return 0.0;
            }
            double directionAverage = directionSum / count;
            double[] best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (double[] coordinate : coordinates) {
                if (Mth.equal(coordinate[1], 0.0)) {
                    continue;
                }
                double distance = Math.abs(coordinate[1] - directionAverage);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = coordinate;
                }
            }
            return best == null ? 0.0 : best[0] / best[1];
        }

        Vec3[] nearestPointsTo(Line other) {
            Double phi1 = this.calculateNearestPhiTo(other);
            Double phi2 = other.calculateNearestPhiTo(this);
            if (phi1 == null || phi2 == null) {
                return null;
            }
            return new Vec3[]{this.getPosition(phi1), other.getPosition(phi2)};
        }

        Vec3 nearestPointTo(AABB box) {
            double px = this.position.x;
            double py = this.position.y;
            double pz = this.position.z;
            double dx = this.direction.x;
            double dy = this.direction.y;
            double dz = this.direction.z;

            double[] breakpoints = new double[6];
            int count = 0;
            if (!Mth.equal(dx, 0.0)) {
                breakpoints[count++] = (box.minX - px) / dx;
                breakpoints[count++] = (box.maxX - px) / dx;
            }
            if (!Mth.equal(dy, 0.0)) {
                breakpoints[count++] = (box.minY - py) / dy;
                breakpoints[count++] = (box.maxY - py) / dy;
            }
            if (!Mth.equal(dz, 0.0)) {
                breakpoints[count++] = (box.minZ - pz) / dz;
                breakpoints[count++] = (box.maxZ - pz) / dz;
            }
            Arrays.sort(breakpoints, 0, count);

            BestPoint best = new BestPoint(this.getPositionChecked(0.0), this.distanceAt(box, 0.0));
            for (int i = 0; i < count; i++) {
                this.evaluate(box, breakpoints[i], best);
            }
            if (count == 0) {
                return best.point == null ? this.getPosition(0.0) : best.point;
            }

            this.evaluateInterval(box, Double.NEGATIVE_INFINITY, breakpoints[0], breakpoints[0] - 1.0, best);
            for (int i = 0; i < count - 1; i++) {
                double start = breakpoints[i];
                double end = breakpoints[i + 1];
                if (start < end) {
                    this.evaluateInterval(box, start, end, (start + end) * 0.5, best);
                }
            }
            this.evaluateInterval(
                    box,
                    breakpoints[count - 1],
                    Double.POSITIVE_INFINITY,
                    breakpoints[count - 1] + 1.0,
                    best);
            return best.point == null ? this.getPosition(0.0) : best.point;
        }

        private double distanceAt(AABB box, double phi) {
            Vec3 point = this.position.add(this.direction.scale(phi));
            double xDiff = point.x < box.minX
                    ? box.minX - point.x
                    : point.x > box.maxX ? point.x - box.maxX : 0.0;
            double yDiff = point.y < box.minY
                    ? box.minY - point.y
                    : point.y > box.maxY ? point.y - box.maxY : 0.0;
            double zDiff = point.z < box.minZ
                    ? box.minZ - point.z
                    : point.z > box.maxZ ? point.z - box.maxZ : 0.0;
            return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
        }

        private void evaluate(AABB box, double phi, BestPoint best) {
            Vec3 point = this.getPositionChecked(phi);
            if (point == null) {
                return;
            }
            double distance = this.distanceAt(box, phi);
            if (distance < best.distance) {
                best.point = point;
                best.distance = distance;
            }
        }

        private void evaluateInterval(
                AABB box,
                double start,
                double end,
                double sample,
                BestPoint best) {
            Vec3 samplePoint = this.position.add(this.direction.scale(sample));
            double quadraticA = 0.0;
            double quadraticB = 0.0;

            if (samplePoint.x < box.minX) {
                quadraticA += this.direction.x * this.direction.x;
                quadraticB += this.direction.x * (this.position.x - box.minX);
            } else if (samplePoint.x > box.maxX) {
                quadraticA += this.direction.x * this.direction.x;
                quadraticB += this.direction.x * (this.position.x - box.maxX);
            }
            if (samplePoint.y < box.minY) {
                quadraticA += this.direction.y * this.direction.y;
                quadraticB += this.direction.y * (this.position.y - box.minY);
            } else if (samplePoint.y > box.maxY) {
                quadraticA += this.direction.y * this.direction.y;
                quadraticB += this.direction.y * (this.position.y - box.maxY);
            }
            if (samplePoint.z < box.minZ) {
                quadraticA += this.direction.z * this.direction.z;
                quadraticB += this.direction.z * (this.position.z - box.minZ);
            } else if (samplePoint.z > box.maxZ) {
                quadraticA += this.direction.z * this.direction.z;
                quadraticB += this.direction.z * (this.position.z - box.maxZ);
            }

            if (Mth.equal(quadraticA, 0.0)) {
                this.evaluate(box, sample, best);
                return;
            }
            double root = -quadraticB / quadraticA;
            boolean inInterval = start == Double.NEGATIVE_INFINITY
                    ? root <= end
                    : end == Double.POSITIVE_INFINITY ? root >= start : root >= start && root <= end;
            if (inInterval) {
                this.evaluate(box, root, best);
            }
        }

        protected Double calculateNearestPhiTo(Line other) {
            double pos1X = other.position.x;
            double pos1Y = other.position.y;
            double pos1Z = other.position.z;
            double dir1X = other.direction.x;
            double dir1Y = other.direction.y;
            double dir1Z = other.direction.z;
            double pos2X = this.position.x;
            double pos2Y = this.position.y;
            double pos2Z = this.position.z;
            double dir2X = this.direction.x;
            double dir2Y = this.direction.y;
            double dir2Z = this.direction.z;

            double divisor =
                    (dir1Y * dir1Y + dir1X * dir1X) * dir2Z * dir2Z
                            + (-2 * dir1Y * dir1Z * dir2Y - 2 * dir1X * dir1Z * dir2X) * dir2Z
                            + (dir1Z * dir1Z + dir1X * dir1X) * dir2Y * dir2Y
                            - 2 * dir1X * dir1Y * dir2X * dir2Y
                            + (dir1Z * dir1Z + dir1Y * dir1Y) * dir2X * dir2X;
            if (Mth.equal(divisor, 0.0)) {
                return null;
            }
            return -(((dir1Y * dir1Y + dir1X * dir1X) * dir2Z
                    - dir1Y * dir1Z * dir2Y
                    - dir1X * dir1Z * dir2X) * pos2Z
                    + (-dir1Y * dir1Z * dir2Z
                    + (dir1Z * dir1Z + dir1X * dir1X) * dir2Y
                    - dir1X * dir1Y * dir2X) * pos2Y
                    + (-dir1X * dir1Z * dir2Z
                    - dir1X * dir1Y * dir2Y
                    + (dir1Z * dir1Z + dir1Y * dir1Y) * dir2X) * pos2X
                    + ((-dir1Y * dir1Y - dir1X * dir1X) * dir2Z
                    + dir1Y * dir1Z * dir2Y
                    + dir1X * dir1Z * dir2X) * pos1Z
                    + (dir1Y * dir1Z * dir2Z
                    + (-dir1Z * dir1Z - dir1X * dir1X) * dir2Y
                    + dir1X * dir1Y * dir2X) * pos1Y
                    + (dir1X * dir1Z * dir2Z
                    + dir1X * dir1Y * dir2Y
                    + (-dir1Z * dir1Z - dir1Y * dir1Y) * dir2X) * pos1X) / divisor;
        }

        private static final class BestPoint {
            private Vec3 point;
            private double distance;

            private BestPoint(Vec3 point, double distance) {
                this.point = point;
                this.distance = distance;
            }
        }
    }

    static final class LineSegment extends Line {
        private final double start;
        private final double end;

        LineSegment(Vec3 position, Vec3 direction, double start, double end) {
            super(position, direction);
            this.start = start;
            this.end = end;
        }

        static LineSegment fromPoints(Vec3 first, Vec3 second) {
            return new LineSegment(first, second.subtract(first), 0.0, 1.0);
        }

        Vec3 firstPoint() {
            return this.getPosition(this.start);
        }

        Vec3 secondPoint() {
            return this.getPosition(this.end);
        }

        @Override
        Vec3 nearestPointTo(Vec3 point) {
            NormalizedPlane plane = new NormalizedPlane(point, this.direction);
            Double intersection = plane.intersectionPhi(this);
            double phi = intersection == null ? this.getPhiForPoint(point) : intersection;
            return super.getPosition(Mth.clamp(phi, this.start, this.end));
        }

        @Override
        protected Double calculateNearestPhiTo(Line other) {
            Double phi = super.calculateNearestPhiTo(other);
            return phi == null ? null : Mth.clamp(phi, this.start, this.end);
        }

        @Override
        Vec3 getPosition(double phi) {
            if (phi < this.start || phi > this.end) {
                throw new IllegalArgumentException("Phi outside line segment");
            }
            return super.getPosition(phi);
        }

        @Override
        Vec3 getPositionChecked(double phi) {
            if (phi < this.start || phi > this.end) {
                return null;
            }
            return super.getPosition(phi);
        }
    }

    static final class NormalizedPlane {
        private final Vec3 position;
        private final Vec3 normal;

        NormalizedPlane(Vec3 position, Vec3 normal) {
            if (position == null || isLikelyZero(normal)) {
                throw new IllegalArgumentException("Plane normal must not be zero");
            }
            this.position = position;
            this.normal = normalizeIfNeeded(normal);
        }

        static NormalizedPlane fromParams(Vec3 base, Vec3 directionA, Vec3 directionB) {
            Vec3 normal = directionA.cross(directionB).normalize();
            if (isLikelyZero(normal)) {
                throw new IllegalArgumentException("Plane directions must not be collinear");
            }
            return new NormalizedPlane(base, normal);
        }

        Double intersectionPhi(Line line) {
            double d = this.position.dot(this.normal);
            double e = line.direction.dot(this.normal);
            if (Mth.equal(e, 0.0)) {
                return null;
            }
            return (d - line.position.dot(this.normal)) / e;
        }

        Vec3 intersection(Line line) {
            Double phi = this.intersectionPhi(line);
            return phi == null ? null : line.getPositionChecked(phi);
        }
    }

    static final class AlignedFace {
        final Vec3 from;
        final Vec3 to;

        AlignedFace(Vec3 from, Vec3 to) {
            this.from = new Vec3(
                    Math.min(from.x, to.x),
                    Math.min(from.y, to.y),
                    Math.min(from.z, to.z));
            this.to = new Vec3(
                    Math.max(from.x, to.x),
                    Math.max(from.y, to.y),
                    Math.max(from.z, to.z));
        }

        double area() {
            Vec3 dimensions = this.dimensions();
            return (dimensions.x * dimensions.y
                    + dimensions.y * dimensions.z
                    + dimensions.x * dimensions.z) * 2.0;
        }

        Vec3 center() {
            return this.from.add(this.to).scale(0.5);
        }

        Vec3 dimensions() {
            return this.to.subtract(this.from);
        }

        AlignedFace requireNonEmpty() {
            return Mth.equal(this.area(), 0.0) ? null : this;
        }

        AlignedFace truncateY(double minY) {
            return new AlignedFace(
                    new Vec3(this.from.x, Math.max(this.from.y, minY), this.from.z),
                    new Vec3(this.to.x, Math.max(this.to.y, minY), this.to.z));
        }

        AlignedFace clamp(AABB box) {
            return new AlignedFace(
                    new Vec3(
                            Mth.clamp(this.from.x, box.minX, box.maxX),
                            Mth.clamp(this.from.y, box.minY, box.maxY),
                            Mth.clamp(this.from.z, box.minZ, box.maxZ)),
                    new Vec3(
                            Mth.clamp(this.to.x, box.minX, box.maxX),
                            Mth.clamp(this.to.y, box.minY, box.maxY),
                            Mth.clamp(this.to.z, box.minZ, box.maxZ)));
        }

        AlignedFace offset(Vec3 vector) {
            return new AlignedFace(this.from.add(vector), this.to.add(vector));
        }

        NormalizedPlane toPlane() {
            Vec3 dimensions = this.dimensions();
            return NormalizedPlane.fromParams(
                    this.from,
                    new Vec3(dimensions.x, dimensions.y, 0.0),
                    new Vec3(0.0, dimensions.y, dimensions.z));
        }

        Vec3 nearestPointTo(Line otherLine) {
            Vec3[] directions = this.directionVectors();
            NormalizedPlane plane = NormalizedPlane.fromParams(this.from, directions[0], directions[1]);
            List<LineSegment> edges = this.edges();
            Vec3 intersection = plane.intersection(otherLine);
            if (intersection != null) {
                boolean inside = true;
                for (LineSegment edge : edges) {
                    Vec3 lineCenter = edge.getPosition(0.5);
                    Vec3 centerDirection = lineCenter.subtract(this.center());
                    Vec3 intersectionDirection = lineCenter.subtract(intersection);
                    if (intersectionDirection.dot(centerDirection) <= 0.0) {
                        inside = false;
                        break;
                    }
                }
                if (edges.isEmpty() || inside) {
                    return intersection;
                }
            }

            Vec3 nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (LineSegment edge : edges) {
                Vec3[] points = edge.nearestPointsTo(otherLine);
                if (points == null) {
                    continue;
                }
                double distance = points[0].distanceToSqr(points[1]);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = points[0];
                }
            }
            return nearest != null ? nearest : intersection != null ? intersection : this.center();
        }

        private List<LineSegment> edges() {
            Vec3[] directions = this.directionVectors();
            List<LineSegment> result = new ArrayList<>(4);
            if (!isLikelyZero(directions[0])) {
                result.add(new LineSegment(this.from, directions[0], 0.0, 1.0));
                result.add(new LineSegment(this.to, directions[0].scale(-1.0), 0.0, 1.0));
            }
            if (!isLikelyZero(directions[1])) {
                result.add(new LineSegment(this.from, directions[1], 0.0, 1.0));
                result.add(new LineSegment(this.to, directions[1].scale(-1.0), 0.0, 1.0));
            }
            return result;
        }

        private Vec3[] directionVectors() {
            Vec3 dimensions = this.dimensions();
            if (Mth.equal(dimensions.x, 0.0)) {
                return new Vec3[]{
                        new Vec3(0.0, dimensions.y, 0.0),
                        new Vec3(0.0, 0.0, dimensions.z)
                };
            }
            if (Mth.equal(dimensions.y, 0.0)) {
                return new Vec3[]{
                        new Vec3(dimensions.x, 0.0, 0.0),
                        new Vec3(0.0, 0.0, dimensions.z)
                };
            }
            if (Mth.equal(dimensions.z, 0.0)) {
                return new Vec3[]{
                        new Vec3(0.0, dimensions.y, 0.0),
                        new Vec3(dimensions.x, 0.0, 0.0)
                };
            }
            throw new IllegalStateException("Face must be axis aligned: " + dimensions);
        }
    }
}
