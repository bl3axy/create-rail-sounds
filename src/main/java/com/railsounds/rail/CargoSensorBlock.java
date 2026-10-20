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
 * Cargo Sensor. Put it touching a Train Station.
 *  - comparator: 0 = no train, 1 = train here but empty, up to 15 = completely full
 *  - plain redstone (all sides, like a lever): ON while the docked train is at/over the threshold
 * Right-click cycles the threshold (25/50/75/90/100%), sneak-right-click switches items/fluids.
 * Works off the server's copy of the train's cargo, so it doesn't need the train loaded in.
 */
public class CargoSensorBlock extends FacingRailBlock {
    public static final IntegerProperty LEVEL = BlockStateProperties.POWER;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold", 0, 4);
    public static final BooleanProperty FLUIDS = BooleanProperty.create("fluids");

    static final int[] PERCENT = {25, 50, 75, 90, 100};
    private static final int RATE = 10;

    public CargoSensorBlock(Properties props) {
        super(props, RailShapes.horizontal(RailShapes.box(2, 0, 3, 14, 11, 13)));
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0).setValue(POWERED, false)
            .setValue(THRESHOLD, 3).setValue(FLUIDS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL, POWERED, THRESHOLD, FLUIDS);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        update(state, level, pos);
        level.scheduleTick(pos, this, RATE);
    }

    private void update(BlockState state, ServerLevel level, BlockPos pos) {
        Train train = StationSensors.dockedTrain(StationSensors.adjacentStation(level, pos));
        int out = 0;
        boolean on = false;
        if (train != null) {
            double fill = StationSensors.fill(train, state.getValue(FLUIDS));
            if (fill < 0) {
                out = 1;
            } else {
                out = 1 + (int) Math.floor(fill * 14 + 1e-6);
                double need = PERCENT[state.getValue(THRESHOLD)] / 100.0;
                on = fill >= need - 1e-3;
            }
        }
        BlockState next = state.setValue(LEVEL, Math.min(15, out)).setValue(POWERED, on);
        if (next != state) level.setBlock(pos, next, Block.UPDATE_ALL);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockState next;
        if (player.isShiftKeyDown()) {
            next = state.setValue(FLUIDS, !state.getValue(FLUIDS));
        } else {
            next = state.setValue(THRESHOLD, (state.getValue(THRESHOLD) + 1) % PERCENT.length);
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (level instanceof ServerLevel sl) update(next, sl, pos);
        player.sendOverlayMessage(status(level.getBlockState(pos), level, pos));
        return InteractionResult.SUCCESS;
    }

    private static Component status(BlockState state, Level level, BlockPos pos) {
        if (!state.hasProperty(THRESHOLD)) return Component.empty();
        String kind = state.getValue(FLUIDS) ? "fluids" : "items";
        Component head = Component.literal("Cargo Sensor: " + kind + ", full at " + PERCENT[state.getValue(THRESHOLD)] + "%")
            .withStyle(ChatFormatting.GOLD);
        if (StationSensors.adjacentStation(level, pos) == null)
            return head.copy().append(Component.literal("  (not touching a station!)").withStyle(ChatFormatting.RED));
        Train t = StationSensors.dockedTrain(StationSensors.adjacentStation(level, pos));
        if (t == null) return head.copy().append(Component.literal("  - no train").withStyle(ChatFormatting.GRAY));
        double fill = StationSensors.fill(t, state.getValue(FLUIDS));
        String f = fill < 0 ? "no " + kind + " storage" : Math.round(fill * 100) + "% full";
        return head.copy().append(Component.literal("  - " + t.name.getString() + ": " + f).withStyle(ChatFormatting.WHITE));
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
