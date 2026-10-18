package com.maxenonyme.highseas.sail.polygon;

import com.maxenonyme.highseas.CreateHighSeas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record PolygonLinkStatePayload(List<BlockPos> nodes) implements CustomPacketPayload {

    public static final Type<PolygonLinkStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "polygon_link_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PolygonLinkStatePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), PolygonLinkStatePayload::nodes,
            PolygonLinkStatePayload::new);

    @Override
    public Type<PolygonLinkStatePayload> type() {
        return TYPE;
    }

    public static void handle(PolygonLinkStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PolygonLinkPreview.set(payload.nodes()));
    }
}
