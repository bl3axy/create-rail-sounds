package com.railsounds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * /diesel and /steam - stand by a station in build (assembly) mode and run one.
 * The train you assemble there gets the diesel horn or steam whistle.
 */
public class TypeCommands {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("diesel")
            .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .executes(ctx -> mark(ctx, TrainTypes.DIESEL)));
        d.register(Commands.literal("steam")
            .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .executes(ctx -> mark(ctx, TrainTypes.STEAM)));
    }

    private static int mark(CommandContext<CommandSourceStack> ctx, String type) {
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 at = ctx.getSource().getPosition();
        StationBlockEntity station = nearestAssemblingStation(level, at, 48);
        if (station == null) {
            ctx.getSource().sendFailure(Component.literal(
                "No station in build mode near you. Put the station in assembly mode, stand by your train, then run /" + type + "."));
            return 0;
        }
        TrainTypes.setPending(TrainTypes.stationKey(level, station.getBlockPos()), type);
        BlockPos p = station.getBlockPos();
        String what = TrainTypes.STEAM.equals(type) ? "steam whistle" : "diesel horn";
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Train at the station (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ") will get the " + what
                + ". Assemble it to lock it in."), false);
        return 1;
    }

    private static StationBlockEntity nearestAssemblingStation(ServerLevel level, Vec3 at, int radius) {
        StationBlockEntity best = null;
        double bestDist = (double) radius * radius;
        int cx = ((int) Math.floor(at.x)) >> 4, cz = ((int) Math.floor(at.z)) >> 4;
        int cr = (radius >> 4) + 1;
        for (int x = cx - cr; x <= cx + cr; x++) {
            for (int z = cz - cr; z <= cz + cr; z++) {
                if (!level.hasChunk(x, z)) continue;
                LevelChunk chunk = level.getChunk(x, z);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof StationBlockEntity sbe) || !sbe.isAssembling()) continue;
                    double dist = Vec3.atCenterOf(sbe.getBlockPos()).distanceToSqr(at);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = sbe;
                    }
                }
            }
        }
        return best;
    }
}
