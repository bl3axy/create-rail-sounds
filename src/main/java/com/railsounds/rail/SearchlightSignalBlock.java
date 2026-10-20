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
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Aspect a;
        try {
            a = compute(level, pos);
        } catch (RuntimeException e) {
            a = Aspect.DARK;
        }
        if (state.getValue(ASPECT) != a) level.setBlock(pos, state.setValue(ASPECT, a), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, RATE);
        if (level.getGameTime() % 1200 == 0) {
            long now = level.getGameTime();
            CHAINS.values().removeIf(c -> c.until < now - CHAIN_REFRESH);
        }
    }

    private static SignalBlockEntity findSignal(Level level, BlockPos pos) {
        // below first (head on top of the signal), then the sides, then above
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction d : order) {
            BlockEntity be = level.getBlockEntity(pos.relative(d));
            if (be instanceof SignalBlockEntity sbe) return sbe;
        }
        return null;
    }

    static Aspect compute(ServerLevel level, BlockPos pos) {
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
        Aspect a = state.getValue(ASPECT);
        String text = switch (a) {
            case DARK -> "Signal dark - put it touching a Create Train Signal that's on the track";
            case RED -> "STOP - block ahead is occupied";
            case YELLOW -> "APPROACH - next signal is red, be ready to stop";
            case GREEN -> "CLEAR";
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
