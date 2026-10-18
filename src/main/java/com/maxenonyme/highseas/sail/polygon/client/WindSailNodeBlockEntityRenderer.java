package com.maxenonyme.highseas.sail.polygon.client;

import com.maxenonyme.highseas.sail.FurlState;
import com.maxenonyme.highseas.sail.polygon.WindSailNodeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class WindSailNodeBlockEntityRenderer implements BlockEntityRenderer<WindSailNodeBlockEntity> {

    private static final ResourceLocation CANVAS = ResourceLocation.fromNamespaceAndPath("create", "block/sail/canvas_white");

    public WindSailNodeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WindSailNodeBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers,
                       int baseLight, int overlay) {
        if (!be.isMaster() || !be.isConstructed())
            return;
        List<BlockPos> nodes = be.nodes();
        if (nodes.size() < 3)
            return;
        Level level = be.getLevel();
        if (level == null)
            return;

        float deployment = effectiveDeployment(level, be);

        BlockPos master = be.getBlockPos();
        List<Vec3> local = new ArrayList<>(nodes.size());
        for (BlockPos p : nodes) {
            local.add(new Vec3(p.getX() + 0.5 - master.getX(),
                    p.getY() + 0.5 - master.getY(),
                    p.getZ() + 0.5 - master.getZ()));
        }

        int topCount = topCount(local);
        Vec3 topMean = mean(local, 0, topCount);
        List<Vec3> rendered = new ArrayList<>(local.size());
        for (int i = 0; i < local.size(); i++) {
            if (i < topCount)
                rendered.add(local.get(i));
            else
                rendered.add(lerp(local.get(i), topMean, 1.0f - deployment));
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(CANVAS);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));

        Vec3 normal = be.localNormal();
        Vec3 u = basisU(local);
        Vec3 v = normal.cross(u).normalize();
        double[] uRange = extent(local, u);
        double[] vRange = extent(local, v);
        double du = Math.max(1.0e-6, uRange[1] - uRange[0]);
        double dv = Math.max(1.0e-6, vRange[1] - vRange[0]);

        int packedLight = sampleLight(level, master);
        Pose pose = ms.last();
        Matrix4f mat = pose.pose();

        drawTriangle(vc, mat, pose, rendered.get(0), rendered.get(1), rendered.get(2),
                local.get(0), local.get(1), local.get(2),
                u, v, uRange[0], vRange[0], du, dv, normal, sprite, packedLight, overlay);
        if (rendered.size() == 4) {
            drawTriangle(vc, mat, pose, rendered.get(0), rendered.get(2), rendered.get(3),
                    local.get(0), local.get(2), local.get(3),
                    u, v, uRange[0], vRange[0], du, dv, normal, sprite, packedLight, overlay);
        }

        if (deployment < 0.999f) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            Matrix4f lineMat = ms.last().pose();
            for (int i = topCount; i < rendered.size(); i++) {
                Vec3 anchor = local.get(i);
                Vec3 tip = rendered.get(i);
                line(lines, lineMat, anchor, tip, 0.8f, 0.75f, 0.5f);
            }
        }
    }

    private static float effectiveDeployment(Level level, WindSailNodeBlockEntity master) {
        float raw = master.deployment();
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, master.getBlockPos());
        if (sub == null)
            return raw;
        float reef = FurlState.amount(sub.getUniqueId(), master.getBlockPos().asLong());
        return 1.0f - reef;
    }

    private static int topCount(List<Vec3> local) {
        if (local.size() == 3)
            return 1;
        int above = 0;
        double meanY = 0.0;
        for (Vec3 v : local)
            meanY += v.y;
        meanY /= local.size();
        for (Vec3 v : local)
            if (v.y >= meanY)
                above++;
        if (above == 0)
            return 1;
        if (above == local.size())
            return local.size() - 1;
        return above;
    }

    private static Vec3 mean(List<Vec3> local, int fromInclusive, int toExclusive) {
        double x = 0, y = 0, z = 0;
        int n = Math.max(1, toExclusive - fromInclusive);
        for (int i = fromInclusive; i < toExclusive; i++) {
            Vec3 v = local.get(i);
            x += v.x;
            y += v.y;
            z += v.z;
        }
        return new Vec3(x / n, y / n, z / n);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static Vec3 basisU(List<Vec3> local) {
        Vec3 u = local.get(1).subtract(local.get(0));
        double len = u.length();
        if (len < 1.0e-6)
            u = new Vec3(1, 0, 0);
        else
            u = u.scale(1.0 / len);
        return u;
    }

    private static double[] extent(List<Vec3> local, Vec3 axis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Vec3 v : local) {
            double d = v.dot(axis);
            if (d < min)
                min = d;
            if (d > max)
                max = d;
        }
        return new double[]{min, max};
    }

    private static int sampleLight(Level level, BlockPos pos) {
        return LevelRenderer.getLightColor(level, pos);
    }

    private static void drawTriangle(VertexConsumer vc, Matrix4f mat, Pose pose,
                                     Vec3 pA, Vec3 pB, Vec3 pC,
                                     Vec3 originalA, Vec3 originalB, Vec3 originalC,
                                     Vec3 u, Vec3 v, double uMin, double vMin, double du, double dv,
                                     Vec3 n, TextureAtlasSprite sprite, int light, int overlay) {
        emit(vc, mat, pose, pA, originalA, u, v, uMin, vMin, du, dv, n, sprite, light, overlay);
        emit(vc, mat, pose, pB, originalB, u, v, uMin, vMin, du, dv, n, sprite, light, overlay);
        emit(vc, mat, pose, pC, originalC, u, v, uMin, vMin, du, dv, n, sprite, light, overlay);
        emit(vc, mat, pose, pC, originalC, u, v, uMin, vMin, du, dv, n, sprite, light, overlay);
    }

    private static void emit(VertexConsumer vc, Matrix4f mat, Pose pose, Vec3 p, Vec3 originalForUv,
                             Vec3 u, Vec3 v, double uMin, double vMin, double du, double dv,
                             Vec3 n, TextureAtlasSprite sprite, int light, int overlay) {
        float uCoord = (float) ((originalForUv.dot(u) - uMin) / du);
        float vCoord = (float) ((originalForUv.dot(v) - vMin) / dv);
        float su = sprite.getU(uCoord);
        float sv = sprite.getV(vCoord);
        vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(su, sv)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    private static void line(VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, float r, float g, float bl) {
        float dx = (float) (b.x - a.x);
        float dy = (float) (b.y - a.y);
        float dz = (float) (b.z - a.z);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-5f)
            return;
        dx /= len;
        dy /= len;
        dz /= len;
        vc.addVertex(mat, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, 1.0f).setNormal(dx, dy, dz);
        vc.addVertex(mat, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, 1.0f).setNormal(dx, dy, dz);
    }

    @Override
    public boolean shouldRenderOffScreen(WindSailNodeBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(WindSailNodeBlockEntity be) {
        if (!be.isMaster())
            return new AABB(be.getBlockPos());
        List<BlockPos> ns = be.nodes();
        if (ns.isEmpty())
            return new AABB(be.getBlockPos());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : ns) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(0.5);
    }
}
