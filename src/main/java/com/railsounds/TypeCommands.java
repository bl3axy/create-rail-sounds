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
        // /railsounds fixtrains - re-measure every coupling and clear stress derails
        d.register(Commands.literal("railsounds")
            .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("fixtrains").executes(TypeCommands::fixTrains)));
    }

    private static int fixTrains(CommandContext<CommandSourceStack> ctx) {
        if (com.zurrtum.create.Create.RAILWAYS == null) return 0;
        int trains = 0, joints = 0, unstuck = 0, skipped = 0;
        for (com.zurrtum.create.content.trains.entity.Train train : com.zurrtum.create.Create.RAILWAYS.trains.values()) {
            boolean touched = false;
            for (int i = 1; i < train.carriages.size() && i - 1 < train.carriageSpacing.size(); i++) {
                try {
                    var prev = train.carriages.get(i - 1).trailingBogey();
                    var next = train.carriages.get(i).leadingBogey();
                    Vec3 a = prev.getAnchorPosition(false), b = next.getAnchorPosition(false);
                    if (a == null || b == null) { skipped++; continue; }
                    double d2 = a.distanceToSqr(b);
                    if (prev.isUpsideDown() != next.isUpsideDown()) d2 -= 4;
                    int gap = (int) Math.round(Math.sqrt(Math.max(0, d2)));
                    if (gap < 1 || gap > 64) { skipped++; continue; }
                    if (train.carriageSpacing.get(i - 1) != gap) {
                        train.carriageSpacing.set(i - 1, gap);
                        joints++;
                        touched = true;
                    }
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
            if (train.derailed) {
                train.derailed = false;
                unstuck++;
                touched = true;
            }
            if (touched) trains++;
        }
        int t = trains, j = joints, u = unstuck, s = skipped;
        ctx.getSource().sendSuccess(() -> Component.literal("Fixed " + j + " coupling gap(s) and freed " + u
            + " stuck train(s) across " + t + " train(s)." + (s > 0 ? " Skipped " + s + " joint(s) in unloaded areas - go near them and run it again." : "")
            + " Rejoin so your game picks up the fixes."), true);
        return 1;
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
