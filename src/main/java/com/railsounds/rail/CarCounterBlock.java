package com.railsounds.rail;

import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Car Counter. Put it touching a Train Station.
 *  - comparator: number of cars in the train stopped there (capped at 15), 0 when empty
 *  - plain redstone: ON while any train is docked
 * Right-click shows the count.
 */
public class CarCounterBlock extends FacingRailBlock {
    public static final IntegerProperty LEVEL = BlockStateProperties.POWER;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final int RATE = 10;

    public CarCounterBlock(Properties props) {
        super(props, RailShapes.horizontal(RailShapes.box(2, 0, 3, 14, 11, 13)));
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL, POWERED);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        Train train = StationSensors.dockedTrain(StationSensors.adjacentStation(level, pos));
        int cars = train == null ? 0 : train.carriages.size();
        BlockState next = state.setValue(LEVEL, Math.min(15, cars)).setValue(POWERED, train != null);
        if (next != state) level.setBlock(pos, next, Block.UPDATE_ALL);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.scheduleTick(pos, this, 1);
        var station = StationSensors.adjacentStation(level, pos);
        Component msg;
        if (station == null) {
            msg = Component.literal("Car Counter: not touching a station!").withStyle(ChatFormatting.RED);
        } else {
            Train t = StationSensors.dockedTrain(station);
            msg = t == null
                ? Component.literal("Car Counter: no train at " + station.name).withStyle(ChatFormatting.GRAY)
                : Component.literal("Car Counter: " + t.name.getString() + " - " + t.carriages.size() + " car"
                    + (t.carriages.size() == 1 ? "" : "s")).withStyle(ChatFormatting.GOLD);
        }
        player.sendOverlayMessage(msg);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(LEVEL);
    }
}
