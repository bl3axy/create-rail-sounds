package com.railsounds.rail;

import com.railsounds.RailSounds;
import com.zurrtum.create.content.trains.signal.TrackEdgePoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Switch Linker - the one tool for hooking switches up to things far away.
 *   1. right-click a Steam 'n' Rails switch block   (it remembers that switch)
 *   2. right-click a lever on an Interlocking Panel, or a Route Sensor   (linked!)
 * You can link the same switch to several things; it keeps remembering it until you click
 * another switch. Right-clicking a switch while sneaking shows where it's set.
 */
public final class RailItems {
    private RailItems() {}

    public static Item SWITCH_LINKER;

    private record Remembered(String dim, UUID switchId, BlockPos pos) {}
    private static final Map<UUID, Remembered> REMEMBERED = new HashMap<>();

    /** player -> the Create Train Signal they last clicked, waiting for a head to point at it */
    private record RememberedSignal(String dim, BlockPos pos) {}
    private static final Map<UUID, RememberedSignal> SIGNAL_MEMORY = new HashMap<>();

    public static void register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "switch_linker"));
        SWITCH_LINKER = Items.registerItem(key, props -> new Item(props.stacksTo(1)));
    }

    public static void clearCache() {
        SIGNAL_MEMORY.clear();
        REMEMBERED.clear();
    }

    /** a player who logs off mid-link leaves nothing behind */
    public static void forget(java.util.UUID player) {
        SIGNAL_MEMORY.remove(player);
        REMEMBERED.remove(player);
        InterlockingPanelBlock.forget(player);
    }

    /** UseBlockCallback: the linker's clicks, and the sneak-click linking flow from the panel */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (SWITCH_LINKER == null || !stack.is(SWITCH_LINKER)) {
            return InterlockingPanelBlock.onUseBlock(player, level, hand, hit);
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean isSwitch = SwitchCompat.isSwitchBlock(level, pos);
        boolean isPanel = state.getBlock() instanceof InterlockingPanelBlock;
        boolean isSensor = state.getBlock() instanceof RouteSensorBlock;
        boolean isHead = state.getBlock() instanceof SearchlightSignalBlock;
        // a Create Train Signal is whatever has one of Create's signal block entities on it
        boolean isCreateSignal = !isHead
            && level.getBlockEntity(pos) instanceof com.zurrtum.create.content.trains.signal.SignalBlockEntity;
        if (!isSwitch && !isPanel && !isSensor && !isHead && !isCreateSignal) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        String dim = level.dimension().identifier().toString();

        // ---- pointing a searchlight head at a signal it doesn't touch ----
        if (isCreateSignal) {
            SIGNAL_MEMORY.put(player.getUUID(), new RememberedSignal(dim, pos.immutable()));
            player.sendOverlayMessage(Component.literal("Train Signal at " + pos.toShortString()
                + " remembered - now click a Searchlight Signal").withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
        if (isHead) {
            if (player.isShiftKeyDown()) {
                SignalLinks.remove(level, pos);
                player.sendOverlayMessage(Component.literal("Link cleared - this head reads whatever it touches")
                    .withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            RememberedSignal rs = SIGNAL_MEMORY.get(player.getUUID());
            if (rs == null) {
                player.sendOverlayMessage(Component.literal("Click a Create Train Signal with the linker first "
                    + "(sneak-click a head to clear its link)").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            if (!rs.dim.equals(dim)) {
                player.sendOverlayMessage(Component.literal("That signal is in another dimension").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            if (!(level.getBlockEntity(rs.pos) instanceof com.zurrtum.create.content.trains.signal.SignalBlockEntity)) {
                SIGNAL_MEMORY.remove(player.getUUID());
                player.sendOverlayMessage(Component.literal("The remembered signal is gone - click it again")
                    .withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            SignalLinks.put(level, pos, rs.pos);
            level.scheduleTick(pos, state.getBlock(), 1);
            player.sendOverlayMessage(Component.literal("Head now reads the signal at " + rs.pos.toShortString())
                .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }
        if (isSwitch) {
            TrackEdgePoint sw = SwitchCompat.switchAt(level, pos);
            if (sw == null) {
                player.sendOverlayMessage(Component.literal("That switch isn't on a track yet").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                player.sendOverlayMessage(Component.literal("Switch is " + SwitchCompat.pretty(SwitchCompat.state(sw))
                    + (SwitchCompat.locked(sw) ? " [locked]" : "") + (SwitchCompat.occupied(sw) ? " [train on it]" : ""))
                    .withStyle(ChatFormatting.GOLD));
                return InteractionResult.SUCCESS;
            }
            REMEMBERED.put(player.getUUID(), new Remembered(dim, sw.getId(), pos.immutable()));
            player.sendOverlayMessage(Component.literal("Switch at " + pos.toShortString()
                + " remembered - now click a panel lever or a route sensor").withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

        Remembered r = REMEMBERED.get(player.getUUID());
        if (r == null) {
            player.sendOverlayMessage(Component.literal("Click a Steam 'n' Rails switch with the linker first").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        if (!r.dim.equals(dim)) {
            player.sendOverlayMessage(Component.literal("That switch is in another dimension").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        if (SwitchCompat.switchById(r.switchId) == null) {
            REMEMBERED.remove(player.getUUID());
            player.sendOverlayMessage(Component.literal("The remembered switch is gone - click it again").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        if (isPanel) {
            int lever = InterlockingPanelBlock.leverAt(state, pos, hit);
            if (lever < 0) {
                player.sendOverlayMessage(Component.literal("Click one of the levers on the front").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            TowerLinks.put(level, pos, lever, r.switchId, r.pos);
            player.sendOverlayMessage(Component.literal("Lever " + (lever + 1) + " now throws the switch at "
                + r.pos.toShortString()).withStyle(ChatFormatting.GREEN));
        } else {
            TowerLinks.put(level, pos, 0, r.switchId, r.pos);
            player.sendOverlayMessage(Component.literal("Route sensor now sets the switch at " + r.pos.toShortString()
                + " (right-click it to pick what it does, Name Tag to pick which trains)").withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }
}
