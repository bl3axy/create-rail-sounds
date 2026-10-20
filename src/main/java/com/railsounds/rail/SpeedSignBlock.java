package com.railsounds.rail;

import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Speed Limit sign. Put it beside the track with the number facing the trains it's for.
 * Trains heading toward the front of the sign take that limit from here on, until the next
 * sign. Trains going the other way (seeing the back) ignore it, just like a real one.
 * Right-click cycles 10/15/20/25/30/40/50/60 mph and "Resume" (lifts the limit).
 */
public class SpeedSignBlock extends FacingRailBlock {
    public static final IntegerProperty LIMIT = IntegerProperty.create("limit", 0, SpeedZones.RESUME);
    private static final int RATE = 2;

    public SpeedSignBlock(Properties props) {
        super(props, RailShapes.horizontal(
            RailShapes.box(7, 0, 7, 9, 9, 9),       // post
            RailShapes.box(3, 8, 6, 13, 16, 8)));   // plate
        registerDefaultState(defaultBlockState().setValue(LIMIT, 4));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIMIT);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) level.scheduleTick(pos, this, 2);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, RATE);
        Direction face = state.getValue(FACING);
        Vec3 normal = new Vec3(face.getStepX(), 0, face.getStepZ());
        // the track usually runs right beside the sign
        AABB area = new AABB(pos).inflate(3.5, 3, 3.5);
        for (CarriageContraptionEntity cce : level.getEntitiesOfClass(CarriageContraptionEntity.class, area, e -> e.isAlive())) {
            Carriage car = cce.getCarriage();
            if (car == null || car.train == null) continue;
            Vec3 heading = SpeedZones.heading(car);
            if (heading == null) continue;
            // the front of the sign looks along `normal`; trains it's meant for drive into it (opposite way)
            if (heading.dot(normal) > -0.5) continue;
            SpeedZones.set(car.train, state.getValue(LIMIT));
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        level.scheduleTick(pos, this, 1); // self-heal if it was placed without its tick (schematic, /setblock with no update)
        int next = (state.getValue(LIMIT) + 1) % (SpeedZones.RESUME + 1);
        level.setBlock(pos, state.setValue(LIMIT, next), Block.UPDATE_ALL);
        String text = next == SpeedZones.RESUME ? "Resume normal speed" : "Speed limit " + SpeedZones.MPH[next] + " mph";
        player.sendOverlayMessage(Component.literal(text + " (for trains facing this side)").withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }
}
