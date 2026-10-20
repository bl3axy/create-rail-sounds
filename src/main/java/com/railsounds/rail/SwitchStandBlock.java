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
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

/**
 * Switch stand with a target, like the ones beside real hand-thrown switches. Put it touching a
 * Steam 'n' Rails Track Switch block. The target shows green (normal / straight) or red
 * (reversed) and turns to match, and right-clicking the stand throws the switch.
 * Won't throw it while a train is on it, or while S&R has it locked by redstone.
 */
public class SwitchStandBlock extends FacingRailBlock {
    public enum Target implements StringRepresentable {
        NONE, NORMAL, REVERSE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Target> TARGET = EnumProperty.create("target", Target.class);
    private static final int RATE = 5;

    public SwitchStandBlock(Properties props) {
        super(props, RailShapes.horizontal(
            RailShapes.box(5, 0, 5, 11, 3, 11),     // base casting
            RailShapes.box(7, 3, 7, 9, 13, 9),      // spindle
            RailShapes.box(4, 10, 7, 12, 16, 9)));  // target (worst case)
        registerDefaultState(defaultBlockState().setValue(TARGET, Target.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TARGET);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 2);
    }

    static TrackEdgePoint adjacentSwitch(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            TrackEdgePoint sw = SwitchCompat.switchAt(level, pos.relative(d));
            if (sw != null) return sw;
        }
        return null;
    }

    private static Target targetFor(TrackEdgePoint sw) {
        if (sw == null) return Target.NONE;
        String s = SwitchCompat.state(sw);
        if (s == null) return Target.NONE;
        return SwitchCompat.NORMAL.equals(s) ? Target.NORMAL : Target.REVERSE;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        Target t = targetFor(adjacentSwitch(level, pos));
        if (state.getValue(TARGET) != t) level.setBlock(pos, state.setValue(TARGET, t), Block.UPDATE_CLIENTS);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!SwitchCompat.available()) {
            player.sendOverlayMessage(Component.literal("Needs Steam 'n' Rails track switches").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        TrackEdgePoint sw = adjacentSwitch(level, pos);
        player.sendOverlayMessage(throwSwitch(sw));
        if (sw != null) {
            level.setBlock(pos, state.setValue(TARGET, targetFor(sw)), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.7f);
        }
        return InteractionResult.SUCCESS;
    }

    /** shared with the interlocking panel: try to move a switch to its next route, and say what happened */
    static Component throwSwitch(TrackEdgePoint sw) {
        if (sw == null)
            return Component.literal("Not touching a Steam 'n' Rails switch block").withStyle(ChatFormatting.RED);
        if (SwitchCompat.locked(sw))
            return Component.literal("Switch is locked (redstone under the switch block)").withStyle(ChatFormatting.RED);
        if (SwitchCompat.occupied(sw))
            return Component.literal("Can't throw - a train is on the switch").withStyle(ChatFormatting.RED);
        String next = SwitchCompat.next(sw);
        if (next == null)
            return Component.literal("This switch only has one route").withStyle(ChatFormatting.GRAY);
        if (!SwitchCompat.set(sw, next))
            return Component.literal("Switch didn't move").withStyle(ChatFormatting.RED);
        return Component.literal("Switch thrown: " + SwitchCompat.pretty(next))
            .withStyle(SwitchCompat.NORMAL.equals(next) ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }
}
