package com.maxenonyme.highseas.sail.polygon;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.config.HighSeasConfig;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PolygonLinker {

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static class Session {
        final UUID subLevelId;
        final List<BlockPos> nodes = new ArrayList<>();

        Session(UUID subLevelId, BlockPos first) {
            this.subLevelId = subLevelId;
            this.nodes.add(first);
        }
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide)
            return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack hand = event.getItemStack();
        if (!(level.getBlockState(pos).getBlock() instanceof WindSailNodeBlock))
            return;
        if (!hand.isEmpty())
            return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!(player instanceof ServerPlayer server))
            return;

        UUID playerId = server.getUUID();
        Session session = SESSIONS.get(playerId);

        if (player.isShiftKeyDown()) {
            if (session != null) {
                SESSIONS.remove(playerId);
                broadcastState(server, List.of());
                tell(server, "message.create_high_seas.polygon.linking_cancelled", ChatFormatting.YELLOW);
                playSound(level, pos, 0.9f);
            }
            return;
        }

        if (!(level.getBlockEntity(pos) instanceof WindSailNodeBlockEntity node))
            return;
        if (node.isLinked()) {
            tell(server, "message.create_high_seas.polygon.already_linked", ChatFormatting.RED);
            return;
        }

        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, pos);
        if (sub == null) {
            tell(server, "message.create_high_seas.polygon.not_a_ship", ChatFormatting.RED);
            return;
        }

        if (session == null) {
            session = new Session(sub.getUniqueId(), pos.immutable());
            SESSIONS.put(playerId, session);
            broadcastState(server, session.nodes);
            tell(server, "message.create_high_seas.polygon.linking_started", ChatFormatting.YELLOW);
            playSound(level, pos, 1.0f);
            return;
        }

        if (!sub.getUniqueId().equals(session.subLevelId)) {
            tell(server, "message.create_high_seas.polygon.cross_ship", ChatFormatting.RED);
            return;
        }

        if (session.nodes.get(0).equals(pos)) {
            attemptClose(server, level, session);
            return;
        }

        if (session.nodes.contains(pos)) {
            return;
        }

        if (session.nodes.size() >= 4) {
            tell(server, "message.create_high_seas.polygon.too_many_nodes", ChatFormatting.RED);
            return;
        }

        BlockPos last = session.nodes.get(session.nodes.size() - 1);
        double maxEdgeSq = HighSeasConfig.polygonMaxEdge * HighSeasConfig.polygonMaxEdge;
        if (last.distSqr(pos) > maxEdgeSq) {
            tell(server, "message.create_high_seas.polygon.edge_too_long", ChatFormatting.RED);
            return;
        }

        session.nodes.add(pos.immutable());
        broadcastState(server, session.nodes);
        playSound(level, pos, 1.1f);
    }

    private static void attemptClose(ServerPlayer player, Level level, Session session) {
        if (session.nodes.size() < 3) {
            tell(player, "message.create_high_seas.polygon.degenerate", ChatFormatting.RED);
            return;
        }

        List<Vec3> local = new ArrayList<>(session.nodes.size());
        for (BlockPos p : session.nodes)
            local.add(SailPolygon.toVec(p));
        double area = SailPolygon.area(local);
        if (area < HighSeasConfig.polygonMinArea) {
            tell(player, "message.create_high_seas.polygon.degenerate", ChatFormatting.RED);
            return;
        }

        double maxEdgeSq = HighSeasConfig.polygonMaxEdge * HighSeasConfig.polygonMaxEdge;
        if (SailPolygon.maxEdgeSquared(local) > maxEdgeSq) {
            tell(player, "message.create_high_seas.polygon.edge_too_long", ChatFormatting.RED);
            return;
        }

        BlockPos masterPos = session.nodes.get(0);
        if (!(level.getBlockEntity(masterPos) instanceof WindSailNodeBlockEntity master))
            return;

        Vec3 center = SailPolygon.centroid(local);
        Vec3 normal = SailPolygon.faceNormal(local);
        int required = SailPolygon.ceilBlocks(area, HighSeasConfig.polygonSailBlockPerArea);

        master.makeMaster(session.nodes, center, normal, area, required, DyeColor.WHITE);

        for (int i = 1; i < session.nodes.size(); i++) {
            BlockPos p = session.nodes.get(i);
            if (level.getBlockEntity(p) instanceof WindSailNodeBlockEntity slave)
                slave.makeSlave(masterPos);
        }

        SESSIONS.remove(player.getUUID());
        broadcastState(player, List.of());
        tell(player, "message.create_high_seas.polygon.closed", ChatFormatting.GREEN);
        level.playSound(null, masterPos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6f, 1.3f);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer server)
            SESSIONS.remove(server.getUUID());
    }

    private static void broadcastState(ServerPlayer player, List<BlockPos> nodes) {
        PacketDistributor.sendToPlayer(player, new PolygonLinkStatePayload(new ArrayList<>(nodes)));
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    private static void playSound(Level level, BlockPos pos, float pitch) {
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.4f, pitch);
    }
}
