package com.railsounds.rail;

import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Hotbox / defect detector. Sits next to the track. Every train that rolls past gets a radio
 * style report in chat for players nearby once the last car clears:
 *   "Detector, milepost 2.4, track 1. Spruce Freight: no defects, total axles 16, speed 31 mph. Detector out."
 * Gives a redstone signal while a train is passing. Right-click toggles chat reports on/off.
 */
public class DefectDetectorBlock extends FacingRailBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    private static final int RATE = 4;
    private static final int CLEAR_TICKS = 30;     // train counts as gone after this long unseen
    private static final double REACH = 4.5;       // blocks from the detector to the car
    private static final int HEARING = 96;         // players this close get the report
    private static final double MPH = 20 * 2.23694; // blocks/tick -> mph (1 block = 1 m)

    private static final class Pass {
        double maxSpeed;
        long lastSeen;
        Component name;
        int axles;
        boolean defect;
    }

    /** dimension|pos -> trains currently going past it */
    private static final Map<String, Map<UUID, Pass>> PASSING = new HashMap<>();

    public DefectDetectorBlock(Properties props) {
        super(props, RailShapes.horizontal(
            RailShapes.box(3, 0, 4, 13, 9, 12),       // cabinet
            RailShapes.box(7.5, 9, 7.5, 8.5, 16, 8.5))); // antenna
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(ENABLED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED, ENABLED);
    }

    public static void clearCache() {
        PASSING.clear();
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.asLong();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        PASSING.remove(key(level, pos));
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        long now = level.getGameTime();
        String k = key(level, pos);
        Map<UUID, Pass> passing = PASSING.computeIfAbsent(k, x -> new HashMap<>());

        AABB area = new AABB(pos).inflate(REACH, 3, REACH);
        boolean any = false;
        for (CarriageContraptionEntity cce : level.getEntitiesOfClass(CarriageContraptionEntity.class, area, e -> e.isAlive())) {
            Carriage car = cce.getCarriage();
            if (car == null || car.train == null) continue;
            Train t = car.train;
            any = true;
            Pass p = passing.computeIfAbsent(t.id, id -> new Pass());
            p.lastSeen = now;
            p.maxSpeed = Math.max(p.maxSpeed, Math.abs(t.speed));
            p.name = t.name;
            p.axles = axles(t);
            p.defect |= t.derailed;
        }

        Iterator<Map.Entry<UUID, Pass>> it = passing.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pass> e = it.next();
            Pass p = e.getValue();
            if (now - p.lastSeen < CLEAR_TICKS) continue;
            it.remove();
            // a train that just sat next to the detector (parked, never moved) doesn't get a report
            if (p.maxSpeed < 0.01) continue;
            if (state.getValue(ENABLED)) report(level, pos, p);
        }
        if (passing.isEmpty()) PASSING.remove(k);

        if (state.getValue(POWERED) != any) level.setBlock(pos, state.setValue(POWERED, any), Block.UPDATE_ALL);
    }

    private static int axles(Train t) {
        int n = 0;
        for (Carriage c : t.carriages) {
            if (c.bogeys.getFirst() != null) n += 2;
            if (c.bogeys.getSecond() != null) n += 2;
        }
        return n;
    }

    static String milepost(BlockPos pos) {
        double miles = Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ()) / 1609.344;
        return String.format(Locale.ROOT, "%.1f", miles);
    }

    private static void report(ServerLevel level, BlockPos pos, Pass p) {
        int mph = (int) Math.round(p.maxSpeed * MPH);
        String name = p.name == null || p.name.getString().isBlank() ? "Train" : p.name.getString();
        Component msg = Component.literal("[Detector MP " + milepost(pos) + "] ").withStyle(ChatFormatting.DARK_AQUA)
            .append(Component.literal(name + ": ").withStyle(ChatFormatting.WHITE))
            .append(p.defect
                ? Component.literal("DEFECT - dragging equipment").withStyle(ChatFormatting.RED)
                : Component.literal("no defects").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(", total axles " + p.axles + ", speed " + mph + " mph. Detector out.")
                .withStyle(ChatFormatting.WHITE));
        double r2 = (double) HEARING * HEARING;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= r2) player.sendSystemMessage(msg);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.0f, 1.2f);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.scheduleTick(pos, this, 1); // self-heal if it was placed without its tick (schematic, /setblock with no update)
        boolean on = !state.getValue(ENABLED);
        level.setBlock(pos, state.setValue(ENABLED, on), Block.UPDATE_ALL);
        player.sendOverlayMessage(Component.literal("Detector MP " + milepost(pos) + ": chat reports " + (on ? "ON" : "OFF"))
            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
