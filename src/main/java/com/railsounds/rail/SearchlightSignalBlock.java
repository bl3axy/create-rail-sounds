package com.railsounds.rail;

import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.graph.EdgePointType;
import com.zurrtum.create.content.trains.graph.TrackGraph;
import com.zurrtum.create.content.trains.signal.SignalBlockEntity;
import com.zurrtum.create.content.trains.signal.SignalBoundary;
import com.zurrtum.create.content.trains.signal.SignalPropagator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Three-aspect searchlight signal head. Put it touching one of Create's Train Signals.
 *   red    - Create's signal is red (block ahead occupied / reserved)
 *   yellow - block ahead is clear but the NEXT signal after it is red (approach - be ready to stop)
 *   green  - clear for at least two blocks
 *   dark   - not touching a working Create signal
 * It only shows aspects; Create's signal still decides who actually stops.
 */
public class SearchlightSignalBlock extends FacingRailBlock {
    public enum Aspect implements StringRepresentable {
        DARK, RED, YELLOW, GREEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Aspect> ASPECT = EnumProperty.create("aspect", Aspect.class);
    private static final int RATE = 4;
    private static final int CHAIN_REFRESH = 100; // re-walk the track ahead every 5 seconds

    private record Chain(Map<UUID, Boolean> signals, long until) {}
    /** signal id + which side -> the signals at the far end of the block ahead */
    private static final Map<String, Chain> CHAINS = new HashMap<>();

    public SearchlightSignalBlock(Properties props) {
        super(props, RailShapes.horizontal(
            RailShapes.box(6.5, 0, 6.5, 9.5, 6, 9.5),     // mast stub
            RailShapes.box(4, 6, 5, 12, 16, 11)));        // head and hood
        registerDefaultState(defaultBlockState().setValue(ASPECT, Aspect.DARK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ASPECT);
    }

    public static int light(BlockState state) {
        return state.getValue(ASPECT) == Aspect.DARK ? 0 : 6;
    }

    public static void clearCache() {
        CHAINS.clear();
        SIGNS.clear();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 1);
    }

    /** a head that's gone shouldn't leave its link behind on file */
    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.getBlockState(pos).is(this)) {
            SignalLinks.remove(level, pos);
            SIGNS.remove(SignalLinks.key(level, pos));
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    /** redstone changing next to a head should be seen now, not up to RATE ticks later */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   net.minecraft.world.level.redstone.Orientation orientation, boolean moved) {
        super.neighborChanged(state, level, pos, block, orientation, moved);
        if (level instanceof ServerLevel sl) sl.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        Aspect a;
        try {
            a = compute(level, pos);
        } catch (RuntimeException e) {
            a = Aspect.DARK;
        }
        if (state.getValue(ASPECT) != a) level.setBlock(pos, state.setValue(ASPECT, a), Block.UPDATE_CLIENTS);
        if (level.getGameTime() % 1200 == 0) {
            long now = level.getGameTime();
            CHAINS.values().removeIf(c -> c.until < now - CHAIN_REFRESH);
            SIGNS.values().removeIf(c -> c.until < now - SIGN_REFRESH);
        }
    }

    /**
     * The Create signal this head reads. An explicit link wins: the Switch Linker can point a
     * head at any signal in the dimension, so a head on a bridge over four tracks, or across a
     * yard, reads the right one. Otherwise it falls back to a signal it is touching, or one
     * touching any Signal Mast it sits on (up to 8 tall).
     */
    private static SignalBlockEntity findSignal(Level level, BlockPos pos) {
        BlockPos linked = SignalLinks.get(level, pos);
        if (linked != null) {
            // don't force-load: a head whose signal is in an unloaded chunk just goes dark
            if (level.isLoaded(linked) && level.getBlockEntity(linked) instanceof SignalBlockEntity sbe) {
                return sbe;
            }
            return null;
        }
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        BlockPos at = pos;
        for (int i = 0; i <= 8; i++) {
            for (Direction d : order) {
                BlockEntity be = level.getBlockEntity(at.relative(d));
                if (be instanceof SignalBlockEntity sbe) return sbe;
            }
            at = at.below();
            if (RailBlocks.SIGNAL_MAST == null || !level.getBlockState(at).is(RailBlocks.SIGNAL_MAST)) break;
        }
        return null;
    }

    /** a Speed Limit Sign this close means the head shows yellow rather than green */
    private static final int SPEED_SIGN_RANGE = 10;
    /** signs sit at track level; no need to sweep ten blocks of air above and below */
    private static final int SPEED_SIGN_HEIGHT = 4;
    /** how long a look for nearby signs is trusted. They don't move often */
    private static final int SIGN_REFRESH = 100;

    private record SignCheck(boolean found, long until) {}
    private static final Map<String, SignCheck> SIGNS = new HashMap<>();

    /**
     * Is there a Speed Limit Sign within range?
     *
     * This is a block scan, and compute() runs every few ticks on every head on the railroad,
     * so the answer is cached for SIGN_REFRESH ticks. Unchecked it was reading 9,261 blocks per
     * head five times a second, which is the sort of thing that quietly eats a server.
     */
    static boolean speedSignNear(Level level, BlockPos pos) {
        if (RailBlocks.SPEED_SIGN == null) return false;
        String key = SignalLinks.key(level, pos);
        long now = level.getGameTime();
        SignCheck c = SIGNS.get(key);
        if (c != null && c.until > now) return c.found;

        boolean found = false;
        int r = SPEED_SIGN_RANGE, h = SPEED_SIGN_HEIGHT;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -h; dy <= h; dy++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.isLoaded(m)) continue;
                    if (level.getBlockState(m).is(RailBlocks.SPEED_SIGN)) {
                        found = true;
                        break outer;
                    }
                }
            }
        }
        SIGNS.put(key, new SignCheck(found, now + SIGN_REFRESH));
        return found;
    }

    static Aspect compute(ServerLevel level, BlockPos pos) {
        // redstone is a hand-thrown stop: it beats whatever the track says
        if (level.hasNeighborSignal(pos)) return Aspect.RED;
        SignalBlockEntity sbe = findSignal(level, pos);
        if (sbe == null) return Aspect.DARK;
        SignalBlockEntity.SignalState own = sbe.getState();
        if (own == SignalBlockEntity.SignalState.RED || own == SignalBlockEntity.SignalState.INVALID) return Aspect.RED;

        SignalBoundary boundary = sbe.getSignal();
        if (boundary == null) return Aspect.RED;
        boolean side = boundary.blockEntities.get(true).containsKey(sbe.getBlockPos());
        if (!side && !boundary.blockEntities.get(false).containsKey(sbe.getBlockPos())) return Aspect.RED;

        TrackGraph graph = graphOf(boundary);
        if (graph == null) return Aspect.RED;
        Map<UUID, Boolean> next = chain(level.getServer(), graph, boundary, side, level.getGameTime());

        boolean nextRed = false;
        for (Map.Entry<UUID, Boolean> e : next.entrySet()) {
            SignalBoundary other = graph.getPoint(EdgePointType.SIGNAL, e.getKey());
            if (other == null) continue;
            boolean otherSide = e.getValue();
            if (other.blockEntities.get(otherSide).isEmpty()) continue; // no signal facing us there
            if (other.cachedStates.get(otherSide) == SignalBlockEntity.SignalState.RED) {
                nextRed = true;
                break;
            }
        }
        if (nextRed || own == SignalBlockEntity.SignalState.YELLOW) return Aspect.YELLOW;
        // a speed restriction ahead is an approach indication too - caution, not clear
        if (speedSignNear(level, pos)) return Aspect.YELLOW;
        return Aspect.GREEN;
    }

    private static TrackGraph graphOf(SignalBoundary boundary) {
        if (Create.RAILWAYS == null || Create.RAILWAYS.trackNetworks == null) return null;
        for (TrackGraph g : Create.RAILWAYS.trackNetworks.values()) {
            if (g.getPoint(EdgePointType.SIGNAL, boundary.id) == boundary) return g;
        }
        return null;
    }

    private static Map<UUID, Boolean> chain(MinecraftServer server, TrackGraph graph, SignalBoundary b, boolean side, long now) {
        String key = b.id + (side ? "+" : "-");
        Chain c = CHAINS.get(key);
        if (c == null || c.until < now) {
            Map<UUID, Boolean> found = SignalPropagator.collectChainedSignals(server, graph, b, side);
            c = new Chain(found, now + CHAIN_REFRESH);
            CHAINS.put(key, c);
        }
        return c.signals;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.scheduleTick(pos, this, 1);
        Aspect a = state.getValue(ASPECT);
        BlockPos linked = SignalLinks.get(level, pos);
        String where = linked == null ? "reading a signal it touches" : "linked to " + linked.toShortString();
        boolean powered = level instanceof ServerLevel sl2 && sl2.hasNeighborSignal(pos);
        String text = switch (a) {
            case DARK -> linked == null
                ? "Signal dark - link it with a Switch Linker, or put it touching a Create Train Signal"
                : "Signal dark - the signal it is linked to at " + linked.toShortString() + " is gone or unloaded";
            case RED -> powered ? "STOP - held by redstone" : "STOP - block ahead is occupied (" + where + ")";
            case YELLOW -> speedSignNear(level, pos)
                ? "APPROACH - speed limit sign within " + SPEED_SIGN_RANGE + " blocks (" + where + ")"
                : "APPROACH - next signal is red, be ready to stop (" + where + ")";
            case GREEN -> "CLEAR (" + where + ")";
        };
        ChatFormatting color = switch (a) {
            case DARK -> ChatFormatting.GRAY;
            case RED -> ChatFormatting.RED;
            case YELLOW -> ChatFormatting.YELLOW;
            case GREEN -> ChatFormatting.GREEN;
        };
        player.sendOverlayMessage(Component.literal(text).withStyle(color));
        return InteractionResult.SUCCESS;
    }
}
