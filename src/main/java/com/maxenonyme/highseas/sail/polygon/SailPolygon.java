package com.maxenonyme.highseas.sail.polygon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SailPolygon {

    private SailPolygon() {
    }

    public static double triangleArea(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 ab = b.subtract(a);
        Vec3 ac = c.subtract(a);
        return 0.5 * ab.cross(ac).length();
    }

    public static double area(List<Vec3> nodes) {
        if (nodes.size() == 3)
            return triangleArea(nodes.get(0), nodes.get(1), nodes.get(2));
        if (nodes.size() == 4)
            return triangleArea(nodes.get(0), nodes.get(1), nodes.get(2))
                    + triangleArea(nodes.get(0), nodes.get(2), nodes.get(3));
        return 0.0;
    }

    public static Vec3 centroid(List<Vec3> nodes) {
        double x = 0, y = 0, z = 0;
        for (Vec3 v : nodes) {
            x += v.x;
            y += v.y;
            z += v.z;
        }
        int n = nodes.size();
        return new Vec3(x / n, y / n, z / n);
    }

    public static Vec3 faceNormal(List<Vec3> nodes) {
        if (nodes.size() < 3)
            return new Vec3(0, 1, 0);
        Vec3 a = nodes.get(0);
        Vec3 b = nodes.get(1);
        Vec3 c = nodes.get(2);
        Vec3 n1 = b.subtract(a).cross(c.subtract(a));
        Vec3 n = n1;
        if (nodes.size() == 4) {
            Vec3 d = nodes.get(3);
            Vec3 n2 = c.subtract(a).cross(d.subtract(a));
            n = n1.add(n2);
        }
        double len = n.length();
        if (len < 1.0e-9)
            return new Vec3(0, 1, 0);
        return n.scale(1.0 / len);
    }

    public static double maxEdgeSquared(List<Vec3> nodes) {
        double best = 0.0;
        int n = nodes.size();
        for (int i = 0; i < n; i++) {
            Vec3 a = nodes.get(i);
            Vec3 b = nodes.get((i + 1) % n);
            double d = a.distanceToSqr(b);
            if (d > best)
                best = d;
        }
        return best;
    }

    public static int ceilBlocks(double area, double multiplier) {
        if (area <= 0.0)
            return 0;
        return (int) Math.ceil(area * multiplier);
    }

    public static Vec3 toVec(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
