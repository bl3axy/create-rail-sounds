package com.railsounds.rail;

import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.signal.TrackEdgePoint;
import com.zurrtum.create.content.trains.station.GlobalStation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Route sensor: automatic switching. Put it beside the track some way BEFORE a switch
 * (15+ blocks so the train isn't on the switch yet) and link it to that switch with the
 * Switch Linker. When a train rolls past, it sets the switch for that train:
 *   right-click with a renamed Name Tag  -> only trains whose NAME or DESTINATION contains that
 *                                           text count as "matching" (plain name tag = every train)
 *   right-click (empty hand)             -> cycle what happens:
 *       0: matching -> Reverse,       everything else -> Normal
 *       1: matching -> Reverse right,  everything else -> Normal   (3-way / wye switches)
 *       2: matching -> Normal,         everything else -> Reverse
 * Works for loose rolling cars too (they match on their name), so it can sort a kicked cut.
 */
public class RouteSensorBlock extends FacingRailBlock {
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    private static final int RATE = 4;
    private static final double REACH = 3.5;
    private static final long FORGET = 60; // ticks after a train has gone before it can trigger again

    /** sensor key -> train id -> last tick seen */
    private static final Map<String, Map<UUID, Long>> SEEN = new HashMap<>();

    public RouteSensorBlock(Properties props) {
        super(props, RailShapes.horizontal(RailShapes.box(4, 0, 4, 12, 5, 12), RailShapes.box(6, 5, 6, 10, 7, 10)));
        registerDefaultState(defaultBlockState().setValue(MODE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE);
    }

    public static void clearCache() {
        SEEN.clear();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.getBlockState(pos).is(this)) {
            TowerLinks.removePanel(level, pos);
            SEEN.remove(TowerLinks.key(level, pos));
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    static boolean matches(Train t, String filter) {
        if (filter == null || filter.isBlank()) return true;
        String f = filter.toLowerCase(Locale.ROOT);
        if (com.railsounds.RailSounds.nameOf(t).toLowerCase(Locale.ROOT).contains(f)) return true;
        try {
            GlobalStation dest = t.navigation.destination;
            return dest != null && dest.name != null && dest.name.toLowerCase(Locale.ROOT).contains(f);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String routeFor(int mode, boolean match, TrackEdgePoint sw) {
        var exits = SwitchCompat.exits(sw);
        String anyReverse = exits.contains(SwitchCompat.LEFT) ? SwitchCompat.LEFT
            : exits.contains(SwitchCompat.RIGHT) ? SwitchCompat.RIGHT : SwitchCompat.NORMAL;
        return switch (mode) {
            case 1 -> match ? (exits.contains(SwitchCompat.RIGHT) ? SwitchCompat.RIGHT : anyReverse) : SwitchCompat.NORMAL;
            case 2 -> match ? SwitchCompat.NORMAL : anyReverse;
            default -> match ? anyReverse : SwitchCompat.NORMAL;
        };
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        TowerLinks.Link link = TowerLinks.get(level, pos, 0);
        if (link == null) return;
        String key = TowerLinks.key(level, pos);
        long now = level.getGameTime();
        Map<UUID, Long> seen = SEEN.computeIfAbsent(key, k -> new HashMap<>());
        seen.values().removeIf(t -> now - t > FORGET);

        AABB area = new AABB(pos).inflate(REACH, 3, REACH);
        for (CarriageContraptionEntity cce : level.getEntitiesOfClass(CarriageContraptionEntity.class, area, e -> e.isAlive())) {
            Carriage car = cce.getCarriage();
            if (car == null || car.train == null) continue;
            Train t = car.train;
            if (Math.abs(t.speed) < 1e-3) continue; // parked next to it doesn't count
            boolean fresh = !seen.containsKey(t.id);
            seen.put(t.id, now);
            if (!fresh) continue;
            TrackEdgePoint sw = SwitchCompat.switchById(link.id());
            if (sw == null || SwitchCompat.locked(sw) || SwitchCompat.occupied(sw)) continue;
            String want = routeFor(state.getValue(MODE), matches(t, link.filter), sw);
            if (!want.equals(SwitchCompat.state(sw))) SwitchCompat.set(sw, want);
        }
        if (seen.isEmpty()) SEEN.remove(key);
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                       InteractionHand hand, BlockHitResult hit) {
        if (stack.is(RailItems.SWITCH_LINKER)) return InteractionResult.PASS; // the linker handles it
        if (!stack.is(Items.NAME_TAG)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        TowerLinks.Link link = TowerLinks.get(level, pos, 0);
        if (link == null) {
            player.sendOverlayMessage(Component.literal("Link a switch first (Switch Linker: click the switch, then this)")
                .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        String filter = name == null ? null : name.getString().trim();
        TowerLinks.setFilter(level, pos, 0, filter == null || filter.isEmpty() ? null : filter);
        player.sendOverlayMessage(Component.literal(filter == null || filter.isEmpty()
                ? "Route sensor: every train matches" : "Route sensor: matching trains named / going to \"" + filter + "\"")
            .withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.scheduleTick(pos, this, 1); // self-heal if it was placed without its tick (schematic, /setblock with no update)
        int mode = player.isShiftKeyDown() ? state.getValue(MODE) : (state.getValue(MODE) + 1) % 3;
        if (mode != state.getValue(MODE)) level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_CLIENTS);
        TowerLinks.Link link = TowerLinks.get(level, pos, 0);
        String who = link == null || link.filter == null ? "every train" : "\"" + link.filter + "\"";
        String what = switch (mode) {
            case 1 -> who + " -> Reverse right, others -> Normal";
            case 2 -> who + " -> Normal, others -> Reverse";
            default -> who + " -> Reverse, others -> Normal";
        };
        String linked = link == null ? "  (no switch linked!)" : "  (switch at " + link.x + " " + link.y + " " + link.z + ")";
        player.sendOverlayMessage(Component.literal("Route sensor: " + what + linked).withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
