package com.maxenonyme.highseas.sail.polygon.client;

import com.maxenonyme.highseas.sail.FurlState;
import com.maxenonyme.highseas.sail.polygon.WindSailNodeBlockEntity;
import com.maxenonyme.highseas.wind.WindManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders a constructed polygon sail as a tessellated bilinear patch between its node tips. Each
 * grid cell carries one full canvas tile so the cloth reads like Create sails; the patch bellies
 * out with the local wind and flutters slightly, and furling pulls the lower edge up to the yard.
 */
public class WindSailNodeBlockEntityRenderer implements BlockEntityRenderer<WindSailNodeBlockEntity> {

    private static final double NODE_TIP = 5.0 / 16.0;
    private static final int MAX_SEGMENTS = 24;
    private static final double IDLE_FILL = 0.35;
    private static final double MAX_BELLY = 1.1;
    private static final double BELLY_PER_SPAN = 0.12;
    private static final double FLUTTER = 0.035;

    private static final class WindState {
        double fill = -1.0;
        double sign = 1.0;
        long lastTick = Long.MIN_VALUE;
    }

    private final Map<WindSailNodeBlockEntity, WindState> windStates = new WeakHashMap<>();

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

        BlockPos master = be.getBlockPos();
        int n = nodes.size();
        Vec3[] anchors = new Vec3[n];
        int[] lights = new int[n];
        for (int i = 0; i < n; i++) {
            BlockPos p = nodes.get(i);
            anchors[i] = tip(level, p).subtract(master.getX(), master.getY(), master.getZ());
            lights[i] = LevelRenderer.getLightColor(level, p);
        }

        float deployment = effectiveDeployment(level, be);
        Vec3[] furled = furl(anchors, deployment);

        // Bilinear patch corners: a triangle is a quad whose last edge collapses onto node 2.
        Vec3[] c = n == 4 ? furled : new Vec3[]{furled[0], furled[1], furled[2], furled[2]};
        int[] l = n == 4 ? lights : new int[]{lights[0], lights[1], lights[2], lights[2]};

        int segU = segments(Math.max(c[0].distanceTo(c[1]), c[3].distanceTo(c[2])));
        int segV = segments(Math.max(c[0].distanceTo(c[3]), c[1].distanceTo(c[2])));

        Vec3 normal = be.localNormal();
        double span = Math.sqrt(Math.max(be.area(), 0.0));
        double belly = Math.min(MAX_BELLY, BELLY_PER_SPAN * span)
                * windFill(level, be, normal, partialTicks) * deployment;
        double time = (level.getGameTime() + partialTicks) * 0.18;
        double flutter = FLUTTER * Math.min(1.0, span / 3.0) * deployment;

        Vec3[][] grid = new Vec3[segU + 1][segV + 1];
        for (int i = 0; i <= segU; i++) {
            double s = i / (double) segU;
            for (int j = 0; j <= segV; j++) {
                double t = j / (double) segV;
                Vec3 base = bilinear(c, s, t);
                double shape = Math.sin(Math.PI * s) * Math.sin(Math.PI * t);
                double wave = flutter * shape * Math.sin(time + s * 5.0 + t * 3.0);
                grid[i][j] = base.add(normal.scale(belly * shape + wave));
            }
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(canvasFor(be));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        Pose pose = ms.last();

        for (int i = 0; i < segU; i++) {
            for (int j = 0; j < segV; j++) {
                Vec3 p00 = grid[i][j], p10 = grid[i + 1][j], p11 = grid[i + 1][j + 1], p01 = grid[i][j + 1];
                Vec3 cellNormal = p10.subtract(p00).add(p11.subtract(p01))
                        .cross(p01.subtract(p00).add(p11.subtract(p10)));
                if (cellNormal.lengthSqr() < 1.0e-12)
                    cellNormal = normal;
                else
                    cellNormal = cellNormal.normalize();
                if (cellNormal.dot(normal) < 0)
                    cellNormal = cellNormal.scale(-1);

                double s0 = i / (double) segU, s1 = (i + 1) / (double) segU;
                double t0 = j / (double) segV, t1 = (j + 1) / (double) segV;
                int l00 = light(l, s0, t0), l10 = light(l, s1, t0), l11 = light(l, s1, t1), l01 = light(l, s0, t1);

                // Front face, then the back face with reversed winding so both sides survive culling.
                emit(vc, pose, p00, 0, 0, cellNormal, sprite, l00, overlay);
                emit(vc, pose, p10, 1, 0, cellNormal, sprite, l10, overlay);
                emit(vc, pose, p11, 1, 1, cellNormal, sprite, l11, overlay);
                emit(vc, pose, p01, 0, 1, cellNormal, sprite, l01, overlay);

                Vec3 back = cellNormal.scale(-1);
                emit(vc, pose, p01, 0, 1, back, sprite, l01, overlay);
                emit(vc, pose, p11, 1, 1, back, sprite, l11, overlay);
                emit(vc, pose, p10, 1, 0, back, sprite, l10, overlay);
                emit(vc, pose, p00, 0, 0, back, sprite, l00, overlay);
            }
        }

        if (deployment < 0.999f) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            Matrix4f lineMat = ms.last().pose();
            for (int i = 0; i < n; i++)
                line(lines, lineMat, anchors[i], furled[i], 0.8f, 0.75f, 0.5f);
        }
    }

    private static Vec3 tip(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.UP;
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(NODE_TIP));
    }

    private static ResourceLocation canvasFor(WindSailNodeBlockEntity be) {
        return ResourceLocation.fromNamespaceAndPath("create", "block/sail/canvas_" + be.color().getSerializedName());
    }

    private static float effectiveDeployment(Level level, WindSailNodeBlockEntity master) {
        float raw = master.deployment();
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, master.getBlockPos());
        if (sub == null)
            return raw;
        float reef = FurlState.amount(sub.getUniqueId(), master.getBlockPos().asLong());
        return 1.0f - reef;
    }

    /**
     * Keeps the highest corners fixed (the yard) and lifts the rest toward them as the sail furls.
     */
    private static Vec3[] furl(Vec3[] anchors, float deployment) {
        int n = anchors.length;
        Integer[] byHeight = new Integer[n];
        for (int i = 0; i < n; i++)
            byHeight[i] = i;
        Arrays.sort(byHeight, (a, b) -> Double.compare(anchors[b].y, anchors[a].y));

        int topCount = n == 3 ? 1 : 2;
        boolean[] top = new boolean[n];
        Vec3 topMean = Vec3.ZERO;
        for (int k = 0; k < topCount; k++) {
            top[byHeight[k]] = true;
            topMean = topMean.add(anchors[byHeight[k]]);
        }
        topMean = topMean.scale(1.0 / topCount);

        Vec3[] out = new Vec3[n];
        for (int i = 0; i < n; i++) {
            if (top[i]) {
                out[i] = anchors[i];
            } else {
                // Gather under the yard rather than collapsing onto a single point.
                Vec3 target = new Vec3(Mth.lerp(0.5, anchors[i].x, topMean.x), topMean.y - 0.15,
                        Mth.lerp(0.5, anchors[i].z, topMean.z));
                out[i] = anchors[i].lerp(target, 1.0f - deployment);
            }
        }
        return out;
    }

    /**
     * Smoothed belly fill in [0, 1] with its side, driven by the wind across the sail's normal.
     */
    private double windFill(Level level, WindSailNodeBlockEntity be, Vec3 localNormal, float partialTicks) {
        WindState state = windStates.computeIfAbsent(be, k -> new WindState());
        long tick = level.getGameTime();
        if (tick != state.lastTick) {
            double target = IDLE_FILL;
            SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, be.getBlockPos());
            Vector3d worldNormal = new Vector3d(localNormal.x, localNormal.y, localNormal.z);
            Vector3d worldCenter = new Vector3d(be.localCenter().x, be.localCenter().y, be.localCenter().z);
            if (sub != null) {
                Pose3dc pose = sub.logicalPose();
                pose.orientation().transform(worldNormal);
                pose.transformPosition(worldCenter);
            }
            Vec3 wind = WindManager.getWind(level, worldCenter.x, worldCenter.y, worldCenter.z).vector();
            double across = wind.x * worldNormal.x + wind.y * worldNormal.y + wind.z * worldNormal.z;
            if (Math.abs(across) > 0.15)
                state.sign = Math.signum(across);
            target += (1.0 - IDLE_FILL) * Mth.clamp(Math.abs(across), 0.0, 1.0);

            if (state.fill < 0 || state.lastTick == Long.MIN_VALUE || tick - state.lastTick > 20)
                state.fill = target;
            else
                state.fill += Mth.clamp(target - state.fill, -0.03, 0.03) * (tick - state.lastTick);
            state.lastTick = tick;
        }
        return state.fill * state.sign;
    }

    private static int segments(double length) {
        return Mth.clamp(Mth.ceil(length), 1, MAX_SEGMENTS);
    }

    private static Vec3 bilinear(Vec3[] c, double s, double t) {
        Vec3 bottom = c[0].lerp(c[1], s);
        Vec3 top = c[3].lerp(c[2], s);
        return bottom.lerp(top, t);
    }

    private static int light(int[] l, double s, double t) {
        double w00 = (1 - s) * (1 - t), w10 = s * (1 - t), w11 = s * t, w01 = (1 - s) * t;
        double block = w00 * LightTexture.block(l[0]) + w10 * LightTexture.block(l[1])
                + w11 * LightTexture.block(l[2]) + w01 * LightTexture.block(l[3]);
        double sky = w00 * LightTexture.sky(l[0]) + w10 * LightTexture.sky(l[1])
                + w11 * LightTexture.sky(l[2]) + w01 * LightTexture.sky(l[3]);
        return LightTexture.pack((int) Math.round(block), (int) Math.round(sky));
    }

    private static void emit(VertexConsumer vc, Pose pose, Vec3 p, float u, float v, Vec3 n,
                             TextureAtlasSprite sprite, int light, int overlay) {
        vc.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(sprite.getU(u), sprite.getV(v))
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
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(MAX_BELLY + 0.5);
    }
}
