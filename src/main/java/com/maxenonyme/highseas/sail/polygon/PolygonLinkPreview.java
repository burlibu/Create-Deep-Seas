package com.maxenonyme.highseas.sail.polygon;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class PolygonLinkPreview {

    private static final List<BlockPos> NODES = new ArrayList<>();

    public static synchronized void set(List<BlockPos> nodes) {
        NODES.clear();
        NODES.addAll(nodes);
    }

    public static synchronized List<BlockPos> snapshot() {
        return new ArrayList<>(NODES);
    }

    public static synchronized void clear() {
        NODES.clear();
    }
}
