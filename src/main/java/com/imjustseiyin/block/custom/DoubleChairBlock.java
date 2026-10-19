package com.imjustseiyin.block.custom;

import com.imjustseiyin.sit.SitUtil;
import com.imjustseiyin.util.LongTablePart;
import com.imjustseiyin.util.VoxelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DoubleChairBlock extends DoubleTableBlock {
    private final double sitHeight;

    public DoubleChairBlock(Properties properties, double sitHeight, VoxelShape voxelShape) {
        super(properties, voxelShape);
        this.sitHeight = sitHeight;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return SitUtil.trySit(level, hit.getBlockPos(), player, sitHeight) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = VoxelHelper.rotateShape(Direction.NORTH, state.getValue(FACING).getOpposite(), SHAPE);
        return state.getValue(PART) == LongTablePart.FOOT ? shape : VoxelHelper.mirrorShapeHorizontally(state.getValue(FACING), shape);
    }
}
