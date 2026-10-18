package com.maxenonyme.highseas.sail.polygon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record PolygonSailGroup(Vec3 localCenter, Vec3 localNormal, double area, BlockPos masterMin,
                               long startTick, long lastSeenTick) {

    public PolygonSailGroup startingAt(long tick) {
        return new PolygonSailGroup(localCenter, localNormal, area, masterMin, tick, lastSeenTick);
    }

    public PolygonSailGroup seenAt(long tick) {
        return new PolygonSailGroup(localCenter, localNormal, area, masterMin, startTick, tick);
    }
}
