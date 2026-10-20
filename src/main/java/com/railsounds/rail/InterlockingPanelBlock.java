package com.railsounds.rail;

import com.zurrtum.create.content.trains.signal.TrackEdgePoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Interlocking panel - the lever frame in a signal tower. Eight levers, each linked to one
 * Steam 'n' Rails track switch anywhere on the network, so a whole yard can be run from one spot.
 *
 *   right-click a lever          throw its switch to the next route
 *   sneak-right-click a lever    start linking it, then sneak-right-click the S&R switch block
 *                                (sneak-click the same lever again to unlink it)
 *   right-click the top/sides    list every lever and where its switch is set
 *
 * Like a real interlocking it won't move a switch with a train on it or one that's locked.
 */
public class InterlockingPanelBlock extends FacingRailBlock {
    public static final int LEVERS = 8;
    private static final long LINK_WINDOW = 20 * 30; // 30 seconds to go click the switch

    private record Pending(String panelKey, BlockPos panel, int lever, long until) {}
    private static final Map<UUID, Pending> LINKING = new HashMap<>();

    public InterlockingPanelBlock(Properties props) {
        super(props, RailShapes.horizontal(
            RailShapes.box(0, 0, 4, 16, 14, 16),    // frame / cabinet
            RailShapes.box(0, 0, 1, 16, 4, 4)));    // lever foot-rail
    }

    public static void clearCache() {
        LINKING.clear();
    }

    /** which lever (0-7) the player clicked on the front, or -1 (top row 1-4, bottom row 5-8, left to right) */
    static int leverAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) return -1;
        Vec3 l = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        // u runs 0..1 from the left edge to the right edge as you stand in front of it
        double u = switch (facing) {
            case NORTH -> 1 - l.x;
            case SOUTH -> l.x;
            case EAST -> 1 - l.z;
            default -> l.z; // WEST
        };
        int col = Math.max(0, Math.min(3, (int) Math.floor(u * 4)));
        int row = l.y >= 0.5 ? 0 : 1;
        return row * 4 + col;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int lever = leverAt(state, pos, hit);
        if (lever < 0) {
            player.sendSystemMessage(overview(level, pos));
            return InteractionResult.SUCCESS;
        }
        String label = "Lever " + (lever + 1) + ": ";

        if (player.isShiftKeyDown()) {
            Pending p = LINKING.get(player.getUUID());
            if (p != null && p.panel.equals(pos) && p.lever == lever && p.panelKey.equals(TowerLinks.key(level, pos))) {
                LINKING.remove(player.getUUID());
                TowerLinks.clear(level, pos, lever);
                player.sendOverlayMessage(Component.literal(label + "unlinked").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            if (!SwitchCompat.available()) {
                player.sendOverlayMessage(Component.literal("Needs Steam 'n' Rails track switches").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            LINKING.put(player.getUUID(), new Pending(TowerLinks.key(level, pos), pos.immutable(), lever, level.getGameTime() + LINK_WINDOW));
            player.sendOverlayMessage(Component.literal(label + "now sneak-right-click a Steam 'n' Rails switch block (sneak-click this lever again to unlink)")
                .withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

        TowerLinks.Link link = TowerLinks.get(level, pos, lever);
        if (link == null) {
            player.sendOverlayMessage(Component.literal(label + "not linked - sneak-right-click it to link a switch").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        TrackEdgePoint sw = SwitchCompat.switchById(link.id());
        if (sw == null) {
            player.sendOverlayMessage(Component.literal(label + "its switch is gone (removed or track rebuilt) - relink it").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        Component result = SwitchStandBlock.throwSwitch(sw);
        player.sendOverlayMessage(Component.literal(label).withStyle(ChatFormatting.WHITE).append(result));
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.9f, 0.6f);
        return InteractionResult.SUCCESS;
    }

    private static Component overview(Level level, BlockPos pos) {
        MutableComponent out = Component.literal("Interlocking panel").withStyle(ChatFormatting.GOLD);
        Map<Integer, TowerLinks.Link> links = TowerLinks.all(level, pos);
        if (links.isEmpty()) {
            return out.append(Component.literal(" - no levers linked. Sneak-right-click a lever, then sneak-right-click a switch.")
                .withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < LEVERS; i++) {
            TowerLinks.Link link = links.get(i);
            if (link == null) continue;
            TrackEdgePoint sw = SwitchCompat.switchById(link.id());
            String where = " (" + link.x + ", " + link.y + ", " + link.z + ")";
            Component state = sw == null
                ? Component.literal("missing - relink").withStyle(ChatFormatting.RED)
                : Component.literal(SwitchCompat.pretty(SwitchCompat.state(sw))
                    + (SwitchCompat.locked(sw) ? " [locked]" : "")
                    + (SwitchCompat.occupied(sw) ? " [occupied]" : ""))
                    .withStyle(SwitchCompat.NORMAL.equals(SwitchCompat.state(sw)) ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
            out.append(Component.literal("\n " + (i + 1) + ": ").withStyle(ChatFormatting.WHITE)).append(state)
                .append(Component.literal(where).withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    /** called from UseBlockCallback: finishes a pending link when the player sneak-clicks a switch block */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator()) return InteractionResult.PASS;
        if (!SwitchCompat.isSwitchBlock(level, hit.getBlockPos())) return InteractionResult.PASS;
        if (level.isClientSide()) {
            // can't see the server's pending link from here; let the server decide, but don't let S&R act twice
            return InteractionResult.PASS;
        }
        Pending p = LINKING.get(player.getUUID());
        if (p == null) return InteractionResult.PASS;
        if (level.getGameTime() > p.until) {
            LINKING.remove(player.getUUID());
            return InteractionResult.PASS;
        }
        if (!p.panelKey.startsWith(level.dimension().identifier() + "|")) {
            player.sendOverlayMessage(Component.literal("That switch is in another dimension from the panel").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        TrackEdgePoint sw = SwitchCompat.switchAt(level, hit.getBlockPos());
        if (sw == null) {
            player.sendOverlayMessage(Component.literal("That switch isn't on a track yet").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        LINKING.remove(player.getUUID());
        TowerLinks.put(level, p.panel, p.lever, sw.getId(), hit.getBlockPos());
        player.sendOverlayMessage(Component.literal("Lever " + (p.lever + 1) + " linked to switch at "
            + hit.getBlockPos().toShortString()).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.getBlockState(pos).is(this)) TowerLinks.removePanel(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }
}
