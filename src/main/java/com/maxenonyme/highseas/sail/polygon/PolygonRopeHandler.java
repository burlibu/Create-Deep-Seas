package com.maxenonyme.highseas.sail.polygon;

import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class PolygonRopeHandler {

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof RopeItem))
            return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        boolean clicked = level.getBlockState(pos).getBlock() instanceof WindSailNodeBlock;
        BlockPos first = stack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
        boolean firstIsNode = first != null && level.getBlockState(first).getBlock() instanceof WindSailNodeBlock;
        if (!clicked && !firstIsNode)
            return;

        if (clicked && isSlave(level, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            tell(event.getEntity(), "Only the master node of a polygon accepts rope.");
            return;
        }
        if (firstIsNode && first != null && isSlave(level, first)) {
            stack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            tell(event.getEntity(), "Only the master node of a polygon accepts rope.");
        }
    }

    private static boolean isSlave(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WindSailNodeBlockEntity n && n.isSlave();
    }

    private static void tell(net.minecraft.world.entity.player.Player player, String text) {
        if (player instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.literal("✖ " + text).withStyle(ChatFormatting.RED), true);
    }
}
