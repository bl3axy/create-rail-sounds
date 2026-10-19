package com.imjustseiyin.block.custom;

import com.imjustseiyin.util.LongTablePart;
import com.imjustseiyin.util.VoxelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Two-block piece (wide tables, benches). FOOT is placed, HEAD is the invisible second half. */
public class DoubleTableBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<LongTablePart> PART = EnumProperty.create("part", LongTablePart.class);
    protected final VoxelShape SHAPE;

    public DoubleTableBlock(Properties properties, VoxelShape voxelShape) {
        super(properties);
        this.SHAPE = voxelShape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, LongTablePart.FOOT));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction == towardsOtherPart(state.getValue(PART), state.getValue(FACING))) {
            if (!(neighborState.is(this) && neighborState.getValue(PART) != state.getValue(PART)))
                return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    private static Direction towardsOtherPart(LongTablePart part, Direction direction) {
        return part == LongTablePart.FOOT ? direction : direction.getOpposite();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockPos other = pos.relative(state.getValue(FACING));
            level.setBlock(other, state.setValue(PART, LongTablePart.HEAD), 3);
            level.updateNeighborsAt(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(PART) == LongTablePart.FOOT) {
            BlockPos other = pos.relative(towardsOtherPart(LongTablePart.FOOT, state.getValue(FACING)));
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this) && otherState.getValue(PART) == LongTablePart.HEAD) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, other, Block.getId(otherState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction direction = ctx.getHorizontalDirection();
        BlockPos other = ctx.getClickedPos().relative(direction);
        Level level = ctx.getLevel();
        return level.getBlockState(other).canBeReplaced(ctx) && level.getWorldBorder().isWithinBounds(other)
            ? defaultBlockState().setValue(FACING, direction) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return VoxelHelper.rotateShape(Direction.NORTH,
            state.getValue(PART) == LongTablePart.FOOT ? state.getValue(FACING).getOpposite() : state.getValue(FACING), SHAPE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == LongTablePart.FOOT ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
}
