package com.maxenonyme.highseas.sail.polygon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * @param pushSign which side of {@code localNormal} the wind drives the hull toward: the side the
 *                 nodes face away from their supports (+1 / -1), or 0 when the rig gives no hint.
 * @param startTick    server tick count ({@code MinecraftServer#getTickCount}), the clock
 *                     {@code SailWindSystem} runs on; never level game time.
 */
public record PolygonSailGroup(Vec3 localCenter, Vec3 localNormal, double area, BlockPos masterMin,
                               int pushSign, long startTick, long lastSeenTick) {

    public PolygonSailGroup startingAt(long tick) {
        return new PolygonSailGroup(localCenter, localNormal, area, masterMin, pushSign, tick, lastSeenTick);
    }

    public PolygonSailGroup seenAt(long tick) {
        return new PolygonSailGroup(localCenter, localNormal, area, masterMin, pushSign, startTick, tick);
    }
}
