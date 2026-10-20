package com.railsounds.rail;

import com.railsounds.Crossings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

/**
 * Railroad crossing signal: crossbuck with two red lights that flash alternately.
 *  - placing it makes a crossing point right here (radius 35) so the bell rings and the
 *    lights flash on their own when a train comes; if there's already a crossing within
 *    12 blocks (another signal on the other side of the road, or one made by command) it
 *    joins that one instead
 *  - sneak-right-click changes the radius (15/25/35/50/75/100), right-click shows it
 *  - a redstone signal into it also makes it flash (for manual / test use)
 *  - while a train is at the crossing it gives out redstone itself, so gates, lamps and
 *    Create contraptions next to it can follow it
 * Breaking the signal removes the crossing point it made.
 */
public class CrossingSignalBlock extends FacingRailBlock {
    public enum Flash implements StringRepresentable {
        OFF, LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Flash> FLASH = EnumProperty.create("flash", Flash.class);
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty TRAIN =
        net.minecraft.world.level.block.state.properties.BooleanProperty.create("train");

    public static final int[] RADII = {15, 25, 35, 50, 75, 100};
    public static final int DEFAULT_RADIUS = 35;
    static final double JOIN_DISTANCE = 12;
    private static final int RATE = 10; // half a second per light, ~60 flashes a minute like the real thing

    public CrossingSignalBlock(Properties props) {
        super(props, RailShapes.horizontal(
            RailShapes.box(7, 0, 7, 9, 16, 9),        // mast
            RailShapes.box(0, 5, 5, 16, 10, 9),       // light bar + both heads
            RailShapes.box(2, 11, 6.5, 14, 16, 7.5)));// crossbuck
        registerDefaultState(defaultBlockState().setValue(FLASH, Flash.OFF).setValue(TRAIN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLASH, TRAIN);
    }

    public static int light(BlockState state) {
        return state.getValue(FLASH) == Flash.OFF ? 0 : 7;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (old.is(this)) return;
        if (level instanceof ServerLevel sl && Crossings.nearest(sl, pos, JOIN_DISTANCE) == null) {
            Crossings.addForBlock(sl, pos, DEFAULT_RADIUS);
        }
        level.scheduleTick(pos, this, 1);
    }

    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.getBlockState(pos).is(this)) Crossings.removeForBlock(pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    /** the crossing this signal belongs to; makes one if there's none (e.g. another signal was broken) */
    static Crossings.Crossing crossing(ServerLevel level, BlockPos pos) {
        Crossings.Crossing c = Crossings.nearest(level, pos, JOIN_DISTANCE);
        if (c == null && level.getBlockState(pos).getBlock() instanceof CrossingSignalBlock) {
            c = Crossings.addForBlock(level, pos, DEFAULT_RADIUS);
        }
        return c;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        Crossings.Crossing c = crossing(level, pos);
        boolean train = c != null && c.isActive();
        boolean on = train || level.hasNeighborSignal(pos);
        Flash f = !on ? Flash.OFF : state.getValue(FLASH) == Flash.LEFT ? Flash.RIGHT : Flash.LEFT;
        BlockState next = state.setValue(FLASH, f).setValue(TRAIN, train);
        if (next != state) {
            // only the TRAIN change needs neighbours told; flashing alone is just looks
            level.setBlock(pos, next, next.getValue(TRAIN) != state.getValue(TRAIN) ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        level.scheduleTick(pos, this, 1); // self-heal if it was placed without its tick (schematic, /setblock with no update)
        Crossings.Crossing c = crossing(sl, pos);
        if (c == null) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            int i = 0;
            while (i < RADII.length && RADII[i] <= c.radius) i++;
            c.radius = RADII[i % RADII.length];
            Crossings.save();
        }
        player.sendOverlayMessage(Component.literal("Crossing '" + c.name + "': radius " + c.radius + " blocks"
            + (c.isActive() ? " - train coming!" : "") + "  (sneak-right-click to change)").withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        // only a real train turns the output on, never redstone coming in - so it can't latch itself
        return state.getValue(TRAIN) ? 15 : 0;
    }
}
