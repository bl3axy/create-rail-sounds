package com.imjustseiyin.block.custom;

import com.imjustseiyin.util.QuadTablePart;
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

/** 2x2 table. FRONTLEFT is placed and rendered, the other three are invisible parts. */
public class QuadTableBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<QuadTablePart> PART = EnumProperty.create("part", QuadTablePart.class);
    protected final VoxelShape SHAPE;

    public QuadTableBlock(Properties properties, VoxelShape voxelShape) {
        super(properties);
        this.SHAPE = voxelShape;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, QuadTablePart.FRONTLEFT));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        QuadTablePart part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        if (direction == towardsBack(part, facing) || direction == towardsSide(part, facing)) {
            if (!(neighborState.is(this) && neighborState.getValue(PART) != part))
                return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    private static Direction towardsBack(QuadTablePart part, Direction direction) {
        return part == QuadTablePart.FRONTLEFT || part == QuadTablePart.FRONTRIGHT ? direction.getOpposite() : direction;
    }

    private static Direction towardsSide(QuadTablePart part, Direction direction) {
        return part == QuadTablePart.FRONTLEFT || part == QuadTablePart.BACKLEFT ? direction.getCounterClockWise() : direction.getClockWise();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            Direction direction = state.getValue(FACING);
            BlockPos backLeft = pos.relative(direction.getOpposite());
            BlockPos backRight = backLeft.relative(direction.getCounterClockWise());
            BlockPos frontRight = backRight.relative(direction);
            level.setBlock(backLeft, state.setValue(PART, QuadTablePart.BACKLEFT), 3);
            level.setBlock(backRight, state.setValue(PART, QuadTablePart.BACKRIGHT), 3);
            level.setBlock(frontRight, state.setValue(PART, QuadTablePart.FRONTRIGHT), 3);
            level.updateNeighborsAt(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(PART) == QuadTablePart.FRONTLEFT) {
            Direction facing = state.getValue(FACING);
            BlockPos backLeft = pos.relative(towardsBack(QuadTablePart.FRONTLEFT, facing));
            BlockPos backRight = backLeft.relative(towardsSide(QuadTablePart.FRONTLEFT, facing));
            BlockPos frontRight = pos.relative(towardsSide(QuadTablePart.FRONTLEFT, facing));
            removeIfMatch(level, player, backRight, QuadTablePart.BACKRIGHT);
            removeIfMatch(level, player, backLeft, QuadTablePart.BACKLEFT);
            removeIfMatch(level, player, frontRight, QuadTablePart.FRONTRIGHT);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private void removeIfMatch(Level level, Player player, BlockPos pos, QuadTablePart part) {
        BlockState s = level.getBlockState(pos);
        if (s.is(this) && s.getValue(PART) == part) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, pos, Block.getId(s));
        }
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction direction = ctx.getHorizontalDirection().getOpposite();
        BlockPos frontLeft = ctx.getClickedPos();
        BlockPos backLeft = frontLeft.relative(direction.getOpposite());
        BlockPos backRight = backLeft.relative(direction.getCounterClockWise());
        BlockPos frontRight = backRight.relative(direction);
        Level level = ctx.getLevel();
        return free(ctx, level, backLeft) && free(ctx, level, backRight) && free(ctx, level, frontRight)
            ? defaultBlockState().setValue(FACING, direction) : null;
    }

    private static boolean free(BlockPlaceContext ctx, Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced(ctx) && level.getWorldBorder().isWithinBounds(pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction f = state.getValue(FACING);
        return switch (state.getValue(PART)) {
            case BACKLEFT -> VoxelHelper.rotateShape(Direction.NORTH, f.getClockWise(), SHAPE);
            case BACKRIGHT -> VoxelHelper.rotateShape(Direction.NORTH, f.getOpposite(), SHAPE);
            case FRONTRIGHT -> VoxelHelper.rotateShape(Direction.NORTH, f.getCounterClockWise(), SHAPE);
            case FRONTLEFT -> VoxelHelper.rotateShape(Direction.NORTH, f, SHAPE);
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == QuadTablePart.FRONTLEFT ? RenderShape.MODEL : RenderShape.INVISIBLE;
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
