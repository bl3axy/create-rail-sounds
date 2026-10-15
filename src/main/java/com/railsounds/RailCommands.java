package com.railsounds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;

/**
 * /railsounds crossing add <name> [radius]   - crossing at where you stand
 * /railsounds crossing remove <name>
 * /railsounds crossing list
 * /railsounds crossing signal <name> <x y z> - redstone block appears here while the bell rings
 * /railsounds crossing nosignal <name>
 * /railsounds test <sound>                   - play a sound where you stand
 * /railsounds debug                          - speed/braking of every train
 * /railsounds reload                         - re-read config/railsounds.json
 */
public class RailCommands {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("railsounds")
            .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("crossing")
                .then(Commands.literal("add")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> add(ctx, RailConfig.V.defaultCrossingRadius))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 256))
                            .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(RailCommands::remove)))
                .then(Commands.literal("list").executes(RailCommands::list))
                .then(Commands.literal("signal")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .executes(RailCommands::signal))))
                .then(Commands.literal("nosignal")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(RailCommands::noSignal))))
            .then(Commands.literal("test")
                .then(Commands.argument("sound", StringArgumentType.word())
                    .suggests((ctx, b) -> {
                        for (String s : new String[]{SoundPlayer.CLACK, SoundPlayer.BRAKE, SoundPlayer.COUPLER, SoundPlayer.AIR, SoundPlayer.BELL})
                            b.suggest(s);
                        return b.buildFuture();
                    })
                    .executes(ctx -> {
                        String s = StringArgumentType.getString(ctx, "sound");
                        SoundPlayer.play(ctx.getSource().getLevel(), ctx.getSource().getPosition(), s, 1f, 1f, 32);
                        ok(ctx, "Played railsounds:" + s);
                        return 1;
                    })))
            .then(Commands.literal("debug").executes(ctx -> {
                if (Create.RAILWAYS == null || Create.RAILWAYS.trains.isEmpty()) {
                    ok(ctx, "No trains.");
                    return 0;
                }
                for (Train t : Create.RAILWAYS.trains.values()) ok(ctx, TrainSoundTracker.describe(t));
                return 1;
            }))
            .then(Commands.literal("reload").executes(ctx -> {
                RailConfig.load();
                Crossings.load();
                ok(ctx, "Reloaded railsounds config + crossings.");
                return 1;
            })));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, int radius) {
        String name = StringArgumentType.getString(ctx, "name");
        if (Crossings.get(name) != null) {
            ctx.getSource().sendFailure(Component.literal("Crossing '" + name + "' already exists. Remove it first."));
            return 0;
        }
        Vec3 p = ctx.getSource().getPosition();
        Crossings.Crossing c = new Crossings.Crossing();
        c.name = name;
        c.dimension = ctx.getSource().getLevel().dimension().identifier().toString();
        c.x = Math.floor(p.x) + 0.5;
        c.y = Math.floor(p.y) + 0.5;
        c.z = Math.floor(p.z) + 0.5;
        c.radius = radius;
        Crossings.LIST.add(c);
        Crossings.save();
        ok(ctx, String.format("Crossing '%s' added at %d %d %d, radius %d.", name, (int) c.x, (int) c.y, (int) c.z, radius));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Crossings.Crossing c = Crossings.get(name);
        if (c == null) {
            ctx.getSource().sendFailure(Component.literal("No crossing called '" + name + "'."));
            return 0;
        }
        Crossings.LIST.remove(c);
        Crossings.save();
        ok(ctx, "Removed crossing '" + name + "'.");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        if (Crossings.LIST.isEmpty()) {
            ok(ctx, "No crossings yet. Stand on one and run /railsounds crossing add <name>");
            return 0;
        }
        for (Crossings.Crossing c : Crossings.LIST) {
            String sig = c.signalX == null ? "" : String.format(" signal@%d %d %d", c.signalX, c.signalY, c.signalZ);
            ok(ctx, String.format("%s: %d %d %d r=%d %s%s", c.name, (int) c.x, (int) c.y, (int) c.z, c.radius, c.dimension, sig));
        }
        return 1;
    }

    private static int signal(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Crossings.Crossing c = Crossings.get(name);
        if (c == null) {
            ctx.getSource().sendFailure(Component.literal("No crossing called '" + name + "'."));
            return 0;
        }
        BlockPos p = BlockPosArgument.getBlockPos(ctx, "pos");
        c.signalX = p.getX();
        c.signalY = p.getY();
        c.signalZ = p.getZ();
        Crossings.save();
        ok(ctx, "Crossing '" + name + "' will put a redstone block at " + p.toShortString() + " while active. Leave that spot empty!");
        return 1;
    }

    private static int noSignal(CommandContext<CommandSourceStack> ctx) {
        Crossings.Crossing c = Crossings.get(StringArgumentType.getString(ctx, "name"));
        if (c == null) return 0;
        c.signalX = c.signalY = c.signalZ = null;
        Crossings.save();
        ok(ctx, "Signal removed.");
        return 1;
    }

    private static void ok(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }
}
