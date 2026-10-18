package com.maxenonyme.highseas.sail.polygon;

import com.maxenonyme.highseas.CreateHighSeas;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class PolygonBreakHandler {

    public static void onBreak(Level level, BlockPos pos, WindSailNodeBlockEntity node, @Nullable Player wrencher) {
        if (level.isClientSide)
            return;
        if (!node.isLinked())
            return;

        WindSailNodeBlockEntity master = node.resolveMaster();
        List<BlockPos> others = new ArrayList<>();
        int inserted = 0;
        if (master != null) {
            inserted = master.consumeInsertedForDrop();
            others.addAll(master.nodes());
            SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, master.getBlockPos());
            if (sub != null)
                PolygonSailRegistry.unpublish(sub.getUniqueId(), master.getBlockPos());
            else
                master.unpublish();
            master.unlink();
        }

        for (BlockPos p : others) {
            if (p.equals(pos))
                continue;
            if (level.getBlockEntity(p) instanceof WindSailNodeBlockEntity other && other != master)
                other.unlink();
        }

        if (inserted > 0)
            dropSailBlocks(level, pos, inserted, wrencher);
    }

    private static void dropSailBlocks(Level level, BlockPos pos, int amount, @Nullable Player wrencher) {
        int maxStack = 64;
        while (amount > 0) {
            int n = Math.min(maxStack, amount);
            amount -= n;
            ItemStack stack = new ItemStack(CreateHighSeas.SAIL_ITEM.get(), n);
            if (wrencher != null && wrencher.getInventory().add(stack))
                continue;
            if (wrencher != null) {
                wrencher.drop(stack, false);
            } else {
                ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                e.setDefaultPickUpDelay();
                level.addFreshEntity(e);
            }
        }
    }
}
