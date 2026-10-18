package com.maxenonyme.highseas.sail.polygon;

import com.maxenonyme.highseas.CreateHighSeas;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.LangBuilder;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WindSailNodeBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity, IHaveHoveringInformation {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private RopeStrandHolderBehavior ropeBehavior;

    // ----- master-only state -----
    private boolean master;
    private final List<BlockPos> nodes = new ArrayList<>();
    private Vec3 localCenter = Vec3.ZERO;
    private Vec3 localNormal = new Vec3(0, 1, 0);
    private double area;
    private int materialRequired;
    private int materialInserted;
    private float deployment = 1.0f;
    private DyeColor color = DyeColor.WHITE;

    // ----- slave-only state -----
    @Nullable
    private BlockPos masterPos;

    // ----- transient (not persisted) -----
    private long startTick = -1L;
    @Nullable
    private UUID publishedSub;
    private final PolygonFurlDriver.RopeState ropeState = new PolygonFurlDriver.RopeState();

    public WindSailNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ropeBehavior = new RopeStrandHolderBehavior(this);
        behaviours.add(ropeBehavior);
    }

    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return ropeBehavior;
    }

    @Override
    public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.UP;
        Vec3i n = facing.getNormal();
        double shift = 5.0 / 16.0;
        return new Vec3(pos.getX() + 0.5 + n.getX() * shift,
                pos.getY() + 0.5 + n.getY() * shift,
                pos.getZ() + 0.5 + n.getZ() * shift);
    }

    public Vec3 anchorWorld() {
        return getAttachmentPoint(worldPosition, getBlockState());
    }

    // ----- master / slave API -----

    public boolean isMaster() {
        return master;
    }

    public boolean isSlave() {
        return masterPos != null;
    }

    public boolean isLinked() {
        return master || masterPos != null;
    }

    public boolean isConstructed() {
        return master && materialRequired > 0 && materialInserted >= materialRequired;
    }

    public List<BlockPos> nodes() {
        return nodes;
    }

    public Vec3 localCenter() {
        return localCenter;
    }

    public Vec3 localNormal() {
        return localNormal;
    }

    public double area() {
        return area;
    }

    public int materialRequired() {
        return materialRequired;
    }

    public int materialInserted() {
        return materialInserted;
    }

    public float deployment() {
        return deployment;
    }

    public DyeColor color() {
        return color;
    }

    @Nullable
    public BlockPos masterPos() {
        return masterPos;
    }

    public void makeMaster(List<BlockPos> ordered, Vec3 localCenter, Vec3 localNormal,
                           double area, int materialRequired, DyeColor color) {
        this.master = true;
        this.masterPos = null;
        this.nodes.clear();
        this.nodes.addAll(ordered);
        this.localCenter = localCenter;
        this.localNormal = localNormal;
        this.area = area;
        this.materialRequired = materialRequired;
        this.materialInserted = 0;
        this.deployment = 1.0f;
        this.color = color;
        setChanged();
        notifyUpdate();
    }

    public void makeSlave(BlockPos master) {
        this.master = false;
        this.nodes.clear();
        this.masterPos = master;
        setChanged();
        notifyUpdate();
    }

    public void unlink() {
        this.master = false;
        this.masterPos = null;
        this.nodes.clear();
        this.materialRequired = 0;
        this.materialInserted = 0;
        this.deployment = 1.0f;
        setChanged();
        notifyUpdate();
    }

    public int insertMaterial(int amount) {
        if (!master || amount <= 0)
            return 0;
        int capacity = Math.max(0, materialRequired - materialInserted);
        int taken = Math.min(capacity, amount);
        if (taken == 0)
            return 0;
        materialInserted += taken;
        setChanged();
        notifyUpdate();
        return taken;
    }

    public void setDeployment(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        if (clamped == deployment)
            return;
        deployment = clamped;
        setChanged();
    }

    public int consumeInsertedForDrop() {
        int n = materialInserted;
        materialInserted = 0;
        return n;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (master) {
            CompoundTag p = new CompoundTag();
            long[] packed = new long[nodes.size()];
            for (int i = 0; i < nodes.size(); i++)
                packed[i] = nodes.get(i).asLong();
            p.put("Nodes", new LongArrayTag(packed));
            ListTag nrm = new ListTag();
            nrm.add(net.minecraft.nbt.DoubleTag.valueOf(localNormal.x));
            nrm.add(net.minecraft.nbt.DoubleTag.valueOf(localNormal.y));
            nrm.add(net.minecraft.nbt.DoubleTag.valueOf(localNormal.z));
            p.put("Normal", nrm);
            ListTag ctr = new ListTag();
            ctr.add(net.minecraft.nbt.DoubleTag.valueOf(localCenter.x));
            ctr.add(net.minecraft.nbt.DoubleTag.valueOf(localCenter.y));
            ctr.add(net.minecraft.nbt.DoubleTag.valueOf(localCenter.z));
            p.put("Center", ctr);
            p.putDouble("Area", area);
            p.putInt("Required", materialRequired);
            p.putInt("Inserted", materialInserted);
            p.putFloat("Deployment", deployment);
            p.putByte("Color", (byte) color.getId());
            tag.put("Polygon", p);
        }
        if (masterPos != null)
            tag.putLong("Master", masterPos.asLong());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        master = false;
        masterPos = null;
        nodes.clear();
        materialRequired = 0;
        materialInserted = 0;
        deployment = 1.0f;
        if (tag.contains("Polygon", 10)) {
            master = true;
            CompoundTag p = tag.getCompound("Polygon");
            long[] packed = p.getLongArray("Nodes");
            for (long l : packed)
                nodes.add(BlockPos.of(l));
            ListTag nrm = p.getList("Normal", 6);
            if (nrm.size() == 3)
                localNormal = new Vec3(nrm.getDouble(0), nrm.getDouble(1), nrm.getDouble(2));
            ListTag ctr = p.getList("Center", 6);
            if (ctr.size() == 3)
                localCenter = new Vec3(ctr.getDouble(0), ctr.getDouble(1), ctr.getDouble(2));
            area = p.getDouble("Area");
            materialRequired = p.getInt("Required");
            materialInserted = p.getInt("Inserted");
            deployment = p.getFloat("Deployment");
            byte c = p.getByte("Color");
            color = DyeColor.byId(c & 0xFF);
        }
        if (tag.contains("Master"))
            masterPos = BlockPos.of(tag.getLong("Master"));
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        if (!(level instanceof ServerLevel server))
            return;
        if (master && isConstructed()) {
            SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, worldPosition);
            if (sub == null) {
                if (publishedSub != null) {
                    PolygonSailRegistry.unpublish(publishedSub, worldPosition);
                    publishedSub = null;
                }
                return;
            }
            long now = server.getGameTime();
            if (startTick < 0)
                startTick = now;
            UUID id = sub.getUniqueId();
            if (publishedSub != null && !publishedSub.equals(id))
                PolygonSailRegistry.unpublish(publishedSub, worldPosition);
            PolygonSailRegistry.publish(id, worldPosition,
                    new PolygonSailGroup(localCenter, localNormal, area, worldPosition, startTick, now));
            publishedSub = id;
            PolygonFurlDriver.tick(this);
        } else if (publishedSub != null) {
            PolygonSailRegistry.unpublish(publishedSub, worldPosition);
            publishedSub = null;
            startTick = -1L;
        }
    }

    public PolygonFurlDriver.RopeState ropeState() {
        return ropeState;
    }

    public void unpublish() {
        if (publishedSub != null && level != null && !level.isClientSide) {
            PolygonSailRegistry.unpublish(publishedSub, worldPosition);
            publishedSub = null;
        }
    }

    @Nullable
    public WindSailNodeBlockEntity resolveMaster() {
        if (master)
            return this;
        if (masterPos == null || level == null)
            return null;
        return level.getBlockEntity(masterPos) instanceof WindSailNodeBlockEntity m ? m : null;
    }

    @Override
    public ItemStack getIcon(boolean isPlayerSneaking) {
        return new ItemStack(CreateHighSeas.SAIL_ITEM.get());
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        lang().translate("polygon.goggle.header").forGoggles(tooltip);

        WindSailNodeBlockEntity m = resolveMaster();
        if (m == null || !m.isMaster()) {
            lang().translate("polygon.goggle.unlinked").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
            lang().translate("polygon.goggle.link_hint").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
            return true;
        }

        int required = Math.max(1, m.materialRequired);
        int inserted = Math.min(m.materialInserted, required);
        boolean done = m.isConstructed();

        lang().translate("polygon.goggle.material").style(ChatFormatting.GRAY).forGoggles(tooltip);
        CreateLang.builder()
                .add(CreateLang.number(inserted).style(done ? ChatFormatting.GREEN : ChatFormatting.GOLD))
                .text(ChatFormatting.GRAY, " / ")
                .add(CreateLang.number(m.materialRequired).style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        int filled = Math.round(PROGRESS_BAR_LENGTH * inserted / (float) required);
        CreateLang.builder()
                .text(done ? ChatFormatting.GREEN : ChatFormatting.GOLD, "█".repeat(filled))
                .text(ChatFormatting.DARK_GRAY, "▒".repeat(PROGRESS_BAR_LENGTH - filled))
                .forGoggles(tooltip, 1);

        if (!done) {
            lang().translate("polygon.goggle.missing", required - inserted)
                    .style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
            lang().translate("polygon.goggle.fill_hint").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
            return true;
        }

        lang().translate("polygon.goggle.complete").style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
        lang().translate("polygon.goggle.area").style(ChatFormatting.GRAY)
                .space()
                .add(CreateLang.number(m.area).text(" m²").style(ChatFormatting.AQUA))
                .forGoggles(tooltip);
        lang().translate("polygon.goggle.deployment").style(ChatFormatting.GRAY)
                .space()
                .add(CreateLang.number(Math.round(m.deployment * 100.0f)).text("%").style(ChatFormatting.AQUA))
                .forGoggles(tooltip);
        return true;
    }

    private static final int PROGRESS_BAR_LENGTH = 20;

    private static LangBuilder lang() {
        return new LangBuilder(CreateHighSeas.MOD_ID);
    }
}
