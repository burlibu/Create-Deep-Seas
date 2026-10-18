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
        UUID subId = sub == null ? null : sub.getUniqueId();
        boolean attached = behavior.isAttached();
        if (!attached) {
            if (subId != null && FurlState.onHalyard(subId, master.getBlockPos())) {
                FurlState.dropReef(subId, master.getBlockPos().asLong());
                ReefSyncPayload.broadcast(subId);
            }
            master.setDeployment(1.0f);
            master.ropeState().reset();
            return;
        }

        ServerRopeStrand strand = strand(behavior);
        if (strand == null)
            return;
        double length = length(strand);
        RopeState state = master.ropeState();
        if (state.restLength < 0.0) {
            state.restLength = length;
            state.lastLength = length;
        }
        if (state.lastLength >= 0.0 && Math.abs(length - state.lastLength) > MOVED) {
            double delta = length - state.lastLength;
            double travel = Math.max(0.5, Math.sqrt(master.area()) * TRAVEL_UNIT);
            float change = (float) (-delta / travel);
            master.setDeployment(master.deployment() + change);
        }
        state.lastLength = length;

        float reef = 1.0f - master.deployment();
        if (subId == null)
            return;
        FurlState.setReef(subId, master.getBlockPos().asLong(), reef);
        if (Math.abs(reef - state.lastSyncedReef) >= SYNC_STEP || reef <= 0.0f || reef >= 1.0f) {
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
        public double restLength = -1.0;
        public double lastLength = -1.0;
        public float lastSyncedReef = -1.0f;

        public void reset() {
            restLength = -1.0;
            lastLength = -1.0;
            lastSyncedReef = -1.0f;
        }
    }
}
