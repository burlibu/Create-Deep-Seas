package com.maxenonyme.highseas.sail.polygon;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.block.BoatSailBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WindSailNodeBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final VoxelShaper SHAPE = VoxelShaper.forDirectional(
            Shapes.or(
                    Block.box(5.0, 0.0, 5.0, 11.0, 2.0, 11.0),
                    Block.box(6.5, 2.0, 6.5, 9.5, 5.0, 9.5)
            ),
            Direction.UP);

    public WindSailNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(WindSailNodeBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindSailNodeBlockEntity(CreateHighSeas.WIND_SAIL_NODE_BE.get(), pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != CreateHighSeas.WIND_SAIL_NODE_BE.get())
            return null;
        return (BlockEntityTicker<T>) new SmartBlockEntityTicker<WindSailNodeBlockEntity>();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof BoatSailBlock))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof WindSailNodeBlockEntity node))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        WindSailNodeBlockEntity master = node.resolveMaster();
        if (master == null || master.isConstructed())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide)
            return ItemInteractionResult.sidedSuccess(true);
        int taken = master.insertMaterial(stack.getCount());
        if (taken > 0) {
            if (!player.isCreative())
                stack.shrink(taken);
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS,
                    0.5f, master.isConstructed() ? 1.4f : 1.0f);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WindSailNodeBlockEntity node)
                PolygonBreakHandler.onBreak(level, pos, node, null);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof WindSailNodeBlockEntity node)
                PolygonBreakHandler.onBreak(level, pos, node, context.getPlayer());
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }
}
