package com.maxenonyme.highseas.sail.polygon;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PolygonSailRegistry {

    private static final Map<UUID, Map<Long, PolygonSailGroup>> BY_SUB = new ConcurrentHashMap<>();

    public static void publish(UUID subId, BlockPos masterPos, PolygonSailGroup group) {
        BY_SUB.computeIfAbsent(subId, k -> new ConcurrentHashMap<>()).put(masterPos.asLong(), group);
    }

    public static void unpublish(UUID subId, BlockPos masterPos) {
        Map<Long, PolygonSailGroup> inner = BY_SUB.get(subId);
        if (inner == null)
            return;
        inner.remove(masterPos.asLong());
        if (inner.isEmpty())
            BY_SUB.remove(subId);
    }

    public static List<PolygonSailGroup> getPolygons(UUID subId) {
        Map<Long, PolygonSailGroup> inner = BY_SUB.get(subId);
        if (inner == null || inner.isEmpty())
            return Collections.emptyList();
        return new ArrayList<>(inner.values());
    }

    public static void forget(UUID subId) {
        BY_SUB.remove(subId);
    }

    public static void clearAll() {
        BY_SUB.clear();
    }
}
