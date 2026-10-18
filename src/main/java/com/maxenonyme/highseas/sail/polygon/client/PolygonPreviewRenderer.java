package com.maxenonyme.highseas.sail.polygon.client;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.sail.polygon.PolygonLinkPreview;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateHighSeas.MOD_ID, value = Dist.CLIENT)
public class PolygonPreviewRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        List<BlockPos> nodes = PolygonLinkPreview.snapshot();
        if (nodes.isEmpty())
            return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return;

        List<Vec3> world = new ArrayList<>(nodes.size());
        for (BlockPos p : nodes) {
            SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, p);
            Vec3 center = Vec3.atCenterOf(p);
            if (sub == null) {
                world.add(center);
            } else {
                Pose3dc pose = sub.logicalPose();
                Vector3d v = new Vector3d(center.x, center.y, center.z);
                pose.transformPosition(v);
                world.add(new Vec3(v.x, v.y, v.z));
            }
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < world.size() - 1; i++)
            line(lines, matrix, world.get(i), world.get(i + 1), 1.0f, 0.85f, 0.2f);

        if (world.size() >= 3) {
            Vec3 last = world.get(world.size() - 1);
            Vec3 first = world.get(0);
            line(lines, matrix, last, first, 1.0f, 0.5f, 0.1f);
        }

        for (Vec3 v : world)
            marker(lines, matrix, v, 0.2f, 1.0f, 0.6f, 0.1f);

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void marker(VertexConsumer vc, Matrix4f mat, Vec3 c, float half, float r, float g, float b) {
        line(vc, mat, c.add(-half, 0, 0), c.add(half, 0, 0), r, g, b);
        line(vc, mat, c.add(0, -half, 0), c.add(0, half, 0), r, g, b);
        line(vc, mat, c.add(0, 0, -half), c.add(0, 0, half), r, g, b);
    }

    private static void line(VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, float r, float g, float bl) {
        float nx = (float) (b.x - a.x);
        float ny = (float) (b.y - a.y);
        float nz = (float) (b.z - a.z);
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-5f)
            return;
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(mat, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 1.0f).setNormal(nx, ny, nz);
        vc.addVertex(mat, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, 1.0f).setNormal(nx, ny, nz);
    }
}
