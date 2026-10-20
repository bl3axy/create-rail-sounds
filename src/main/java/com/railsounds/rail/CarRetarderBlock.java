package com.railsounds.rail;

import com.railsounds.RailConfig;
import com.railsounds.shunt.Shunting;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Car retarder, the hump-yard brake. Put it right beside the track. Loose cars (no loco,
 * nobody driving) rolling past get squeezed down to a set speed, so a kicked cut runs into
 * the yard and couples gently instead of slamming the cars already there.
 * Trains with a loco / a driver are left alone. Redstone turns it OFF (let cars through).
 * Right-click cycles the exit speed: coupling speed / 2 / 4 / 6 blocks per second.
 */
public class CarRetarderBlock extends FacingRailBlock {
    public static final IntegerProperty SETTING = IntegerProperty.create("setting", 0, 3);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final double[] EXIT_BPS = {-1, 2, 4, 6}; // -1 = whatever couples
    private static final double REACH = 2.5;
    private static final double GRIP = 0.02; // blocks/tick taken off per tick while squeezing

    public CarRetarderBlock(Properties props) {
        super(props, RailShapes.horizontal(RailShapes.box(0, 0, 4, 16, 4, 12)));
        registerDefaultState(defaultBlockState().setValue(SETTING, 0).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SETTING, POWERED);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 1);
    }

    private static double exitSpeed(BlockState state) {
        double bps = EXIT_BPS[state.getValue(SETTING)];
        return bps < 0 ? RailConfig.V.coupleMaxSpeed * 0.8 : bps / 20.0;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, 1);
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            state = state.setValue(POWERED, powered);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        if (powered) return;
        double cap = exitSpeed(state);
        Set<Train> done = new HashSet<>();
        AABB area = new AABB(pos).inflate(REACH, 2, REACH);
        for (CarriageContraptionEntity cce : level.getEntitiesOfClass(CarriageContraptionEntity.class, area, e -> e.isAlive())) {
            Carriage car = cce.getCarriage();
            if (car == null || car.train == null) continue;
            Train t = car.train;
            if (!done.add(t)) continue;
            if (Shunting.hasControls(t) || t.manualTick || t.navigation.destination != null) continue;
            double s = Math.abs(t.speed);
            if (s <= cap) continue;
            t.speed = Math.signum(t.speed) * Math.max(cap, s - GRIP);
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.scheduleTick(pos, this, 1); // self-heal if it was placed without its tick (schematic, /setblock with no update)
        int next = (state.getValue(SETTING) + 1) % EXIT_BPS.length;
        level.setBlock(pos, state.setValue(SETTING, next), Block.UPDATE_CLIENTS);
        String text = EXIT_BPS[next] < 0 ? "coupling speed" : (int) EXIT_BPS[next] + " blocks/sec";
        player.sendOverlayMessage(Component.literal("Retarder: loose cars leave at " + text
            + (state.getValue(POWERED) ? " (OFF - powered by redstone)" : "")).withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
