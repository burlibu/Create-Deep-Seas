package com.maxenonyme.highseas.sail.polygon;

import com.maxenonyme.highseas.sail.FurlState;
import com.maxenonyme.highseas.sail.ReefSyncPayload;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.UUID;

public class PolygonFurlDriver {

    private static final double TRAVEL_UNIT = 4.0;
    private static final double MOVED = 1.0e-4;
    private static final float SYNC_STEP = 0.01f;

    public static void tick(WindSailNodeBlockEntity master) {
        if (master.getLevel() == null || master.getLevel().isClientSide)
            return;
        if (!(master.getLevel() instanceof ServerLevel server))
            return;
        if (!master.isMaster() || !master.isConstructed())
            return;
        RopeStrandHolderBehavior behavior = master.getBehavior();
        if (behavior == null)
            return;

        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(server, master.getBlockPos());
        RopeState state = master.ropeState();
        ServerRopeStrand strand = behavior.isAttached() ? strand(behavior) : null;
        if (strand == null) {
            // Losing the winch freezes the sail where it is; only the length baseline is dropped so
            // a newly tied rope starts measuring from its own length.
            state.lastLength = -1.0;
        } else {
            double length = length(strand);
            if (state.lastLength >= 0.0 && Math.abs(length - state.lastLength) > MOVED) {
                double delta = length - state.lastLength;
                double travel = Math.max(0.5, Math.sqrt(master.area()) * TRAVEL_UNIT);
                float change = (float) (-delta / travel);
                master.setDeployment(master.deployment() + change);
            }
            state.lastLength = length;
        }

        // Publish every tick, rope or not: FurlState is not persisted, the deployment is.
        if (sub == null)
            return;
        UUID subId = sub.getUniqueId();
        float reef = 1.0f - master.deployment();
        FurlState.setReef(subId, master.getBlockPos().asLong(), reef);
        boolean extreme = reef <= 0.0f || reef >= 1.0f;
        if (reef != state.lastSyncedReef && (extreme || Math.abs(reef - state.lastSyncedReef) >= SYNC_STEP)) {
            state.lastSyncedReef = reef;
            ReefSyncPayload.broadcast(subId);
        }
    }

    private static ServerRopeStrand strand(RopeStrandHolderBehavior behavior) {
        ServerRopeStrand owned = behavior.getOwnedStrand();
        return owned != null ? owned : behavior.getAttachedStrand();
    }

    private static double length(ServerRopeStrand strand) {
        return strand.getExtension() + (strand.getPoints().size() - 2) * ServerRopeStrand.SEGMENT_LENGTH;
    }

    public static class RopeState {
        public double lastLength = -1.0;
        public float lastSyncedReef = -1.0f;
    }
}
