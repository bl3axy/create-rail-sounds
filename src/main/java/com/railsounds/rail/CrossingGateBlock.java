package com.railsounds.rail;

import com.railsounds.Crossings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Crossing gate. Mast with a striped arm that swings down across the road (the arm reaches
 * about 1.5 blocks to the left of the mast as you face the gate's front - put one on each side
 * of the road for both lanes).
 * Lowers when a train is at the nearest crossing (within 12 blocks) or when it gets redstone,
 * a couple of seconds after the lights start like a real one, and goes back up once it's clear.
 * Right-click to see what it's following.
 */
public class CrossingGateBlock extends FacingRailBlock {
    /** 0 = up, 1 = halfway, 2 = down */
    public static final IntegerProperty ANGLE = IntegerProperty.create("angle", 0, 2);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final int RATE = 10;
    private static final int LOWER_DELAY = 4;   // x RATE ticks = 2 s of lights before the arm moves

    /** gate pos -> how many checks in a row it's been told to lower */
    private static final java.util.Map<String, Integer> WARNED = new java.util.HashMap<>();

    public CrossingGateBlock(Properties props) {
        super(props, RailShapes.horizontal(
            RailShapes.box(6, 0, 6, 10, 16, 10),     // mast
            RailShapes.box(4, 0, 4, 12, 3, 12)));    // base
        registerDefaultState(defaultBlockState().setValue(ANGLE, 0).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ANGLE, LIT);
    }

    public static int light(BlockState state) {
        return state.getValue(LIT) ? 5 : 0;
    }

    public static void clearCache() {
        WARNED.clear();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.getBlockState(pos).is(this)) WARNED.remove(key(level, pos));
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.asLong();
    }

    private static boolean wantsDown(ServerLevel level, BlockPos pos) {
        if (level.hasNeighborSignal(pos)) return true;
        Crossings.Crossing c = Crossings.nearest(level, pos, CrossingSignalBlock.JOIN_DISTANCE);
        return c != null && c.isActive();
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        boolean down = wantsDown(level, pos);
        String key = key(level, pos);
        int warned = down ? WARNED.getOrDefault(key, 0) + 1 : 0;
        if (down) WARNED.put(key, warned); else WARNED.remove(key);

        int angle = state.getValue(ANGLE);
        int target = down ? (warned > LOWER_DELAY ? 2 : angle) : 0;
        int nextAngle = angle == target ? angle : angle + Integer.signum(target - angle);
        // arm lights flash while lowering / down, like the three lamps on a real arm
        boolean lit = down && !state.getValue(LIT);
        BlockState next = state.setValue(ANGLE, nextAngle).setValue(LIT, lit);
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            if (nextAngle != angle) {
                level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.25f, 0.6f);
            }
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        level.scheduleTick(pos, this, 1); // self-heal if it was placed without its tick (schematic, /setblock with no update)
        Crossings.Crossing c = Crossings.nearest(sl, pos, CrossingSignalBlock.JOIN_DISTANCE);
        String text = c == null
            ? "Gate: no crossing within 12 blocks - only redstone will lower it"
            : "Gate: following crossing '" + c.name + "'" + (c.isActive() ? " (train coming)" : "");
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
