package com.railsounds.rail;

import com.zurrtum.create.content.trains.signal.TrackEdgePoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Switch machine: the motor box that throws a switch by wire. Put it touching a Steam 'n' Rails
 * Track Switch block and drive it with plain redstone:
 *   no power        -> Normal (straight)
 *   power 1-7       -> Reverse left   (or the only reverse route if there's just one)
 *   power 8-15      -> Reverse right  (or the only reverse route)
 * It only acts when its input CHANGES, so a switch stand or the interlocking panel can still
 * throw the same switch by hand in between. Pair it with Create Redstone Links to work switches
 * from anywhere - a lever bank in a tower, a detector, a Train Observer...
 * Comparator out: 0 normal, 7 reverse left, 15 reverse right.
 */
public class SwitchMachineBlock extends FacingRailBlock {
    /** the input level last acted on */
    public static final IntegerProperty INPUT = BlockStateProperties.POWER;
    /** what the switch was showing at the last check, for the comparator */
    public static final IntegerProperty ROUTE = IntegerProperty.create("route", 0, 2);
    private static final int RATE = 4;

    public SwitchMachineBlock(Properties props) {
        super(props, RailShapes.horizontal(RailShapes.box(2, 0, 3, 14, 8, 13)));
        registerDefaultState(defaultBlockState().setValue(INPUT, 0).setValue(ROUTE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(INPUT, ROUTE);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (old.is(this)) return;
        // start from whatever the wire is doing now, so placing it doesn't yank the switch over
        if (level instanceof ServerLevel sl) {
            level.setBlock(pos, state.setValue(INPUT, sl.getBestNeighborSignal(pos)), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, 2);
    }

    static String wanted(TrackEdgePoint sw, int power) {
        if (power <= 0) return SwitchCompat.NORMAL;
        List<String> exits = SwitchCompat.exits(sw);
        boolean left = exits.contains(SwitchCompat.LEFT), right = exits.contains(SwitchCompat.RIGHT);
        if (left && right) return power >= 8 ? SwitchCompat.RIGHT : SwitchCompat.LEFT;
        if (left) return SwitchCompat.LEFT;
        if (right) return SwitchCompat.RIGHT;
        return SwitchCompat.NORMAL;
    }

    private static int routeIndex(String state) {
        if (SwitchCompat.LEFT.equals(state)) return 1;
        if (SwitchCompat.RIGHT.equals(state)) return 2;
        return 0;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        TrackEdgePoint sw = SwitchStandBlock.adjacentSwitch(level, pos);
        int power = level.getBestNeighborSignal(pos);
        BlockState next = state;
        if (power != state.getValue(INPUT)) {
            next = next.setValue(INPUT, power);
            if (sw != null && !SwitchCompat.locked(sw) && !SwitchCompat.occupied(sw)) {
                String want = wanted(sw, power);
                if (!want.equals(SwitchCompat.state(sw)) && SwitchCompat.set(sw, want)) {
                    level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4f, 0.7f);
                }
            } else if (sw != null) {
                // couldn't move it right now (train on it / locked): keep the old input so we retry
                next = next.setValue(INPUT, state.getValue(INPUT));
            }
        }
        next = next.setValue(ROUTE, sw == null ? 0 : routeIndex(SwitchCompat.state(sw)));
        if (next != state) level.setBlock(pos, next, Block.UPDATE_ALL);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        TrackEdgePoint sw = SwitchStandBlock.adjacentSwitch(sl, pos);
        Component msg = sw == null
            ? Component.literal("Switch machine: not touching a Steam 'n' Rails switch block").withStyle(ChatFormatting.RED)
            : Component.literal("Switch machine: switch is " + SwitchCompat.pretty(SwitchCompat.state(sw))
                + ", input " + sl.getBestNeighborSignal(pos) + " -> " + SwitchCompat.pretty(wanted(sw, sl.getBestNeighborSignal(pos)))
                + (SwitchCompat.locked(sw) ? " [locked]" : "") + (SwitchCompat.occupied(sw) ? " [train on it]" : ""))
                .withStyle(ChatFormatting.GOLD);
        player.sendOverlayMessage(msg);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return switch (state.getValue(ROUTE)) {
            case 1 -> 7;
            case 2 -> 15;
            default -> 0;
        };
    }
}
