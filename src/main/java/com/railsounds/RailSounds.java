package com.railsounds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.railsounds.shunt.Shunting;
import com.railsounds.shunt.YardRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create Rail Sounds - server-side mod.
 * Watches every Create train each tick and plays sounds that react to what the train
 * is actually doing (speed, braking, starting, stopping, passing crossings).
 * The sounds themselves live in the server resource pack under the "railsounds" namespace,
 * so players only need the resource pack, not this mod.
 */
public class RailSounds implements ModInitializer {
    public static final String MOD_ID = "railsounds";

    /**
     * A train's name for display. Create always sets one, but Trains can also be built by
     * other mods (Steam 'n' Rails splits and combines them), so don't assume.
     */
    public static String nameOf(com.zurrtum.create.content.trains.entity.Train train) {
        if (train == null || train.name == null) return "Train";
        String n = train.name.getString();
        return n.isBlank() ? "Train" : n;
    }

    public static final Logger LOG = LoggerFactory.getLogger("RailSounds");
    private static long lastErrorLog;

    /**
     * Run one per-tick job. Nothing here is worth crashing a server over, so a failure is
     * logged and the next job still runs; the throttle is shared so a fault that repeats every
     * tick writes one stack trace a minute instead of twelve hundred.
     */
    private static void safely(String what, Runnable job) {
        try {
            job.run();
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLog > 60_000) {
                lastErrorLog = now;
                LOG.error("[railsounds] " + what + " tick failed (further errors hidden for 60s)", t);
            }
        }
    }

    /**
     * Same idea for the start and stop hooks, but never throttled: these run once, and if two
     * of them fail I want to see both, not the first one only.
     */
    private static void guarded(String what, Runnable job) {
        try {
            job.run();
        } catch (Throwable t) {
            LOG.error("[railsounds] " + what + " failed", t);
        }
    }

    @Override
    public void onInitialize() {
        RailConfig.load();
        Crossings.load();
        com.railsounds.schedule.ScheduleTypes.register();
        com.railsounds.rail.RailBlocks.register();
        PayloadTypeRegistry.clientboundPlay().register(HornPayload.TYPE, HornPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShuntPayload.TYPE, ShuntPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AutoCouplePayload.TYPE, AutoCouplePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) -> Shunting.sendAutoState(handler.player));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
            (handler, server) -> com.railsounds.rail.RailItems.forget(handler.player.getUUID()));
        ServerPlayNetworking.registerGlobalReceiver(ShuntPayload.TYPE, (p, ctx) -> {
            try {
                if (p.action() == Engines.TOGGLE) {
                    Engines.onKey(ctx.player());
                    return;
                }
                if (p.action() == Bells.TOGGLE) {
                    Bells.onKey(ctx.player());
                    return;
                }
                Shunting.onKey(ctx.player(), p.action(), p.number());
            } catch (RuntimeException e) {
                LOG.error("[railsounds] shunting key failed", e);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // guarded one by one: a corrupt save file for one feature shouldn't stop the rest
            // of them loading, which is what happened when these ran as one block
            guarded("reset", TrainSoundTracker::reset);
            guarded("train types load", () -> TrainTypes.load(server));
            guarded("shunting reset", Shunting::reset);
            guarded("yard rules load", () -> YardRules.load(server));
            guarded("engine state load", () -> Engines.load(server));
            guarded("bell state load", () -> Bells.load(server));
            guarded("tower links load", () -> com.railsounds.rail.TowerLinks.load(server));
            guarded("signal links load", () -> com.railsounds.rail.SignalLinks.load(server));
            guarded("speed zones reset", com.railsounds.rail.SpeedZones::reset);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // every one of these has to run even if an earlier one throws. In singleplayer the
            // same JVM goes straight into the next world, so anything left behind here is state
            // from the old world leaking into the new one.
            guarded("sound queue clear", SoundPlayer::clear);
            guarded("crossings save", Crossings::onServerStop);
            guarded("tracker reset", TrainSoundTracker::reset);
            guarded("horns reset", Horns::reset);
            guarded("train types unload", TrainTypes::unload);
            guarded("shunting reset", Shunting::reset);
            guarded("yard rules unload", YardRules::unload);
            guarded("engine state unload", Engines::unload);
            guarded("bell state unload", Bells::unload);
            guarded("tower links unload", com.railsounds.rail.TowerLinks::unload);
            guarded("signal links unload", com.railsounds.rail.SignalLinks::unload);
            guarded("speed zones reset", com.railsounds.rail.SpeedZones::reset);
            guarded("block caches clear", () -> {
                com.railsounds.rail.SearchlightSignalBlock.clearCache();
                com.railsounds.rail.DefectDetectorBlock.clearCache();
                com.railsounds.rail.InterlockingPanelBlock.clearCache();
                com.railsounds.rail.CrossingGateBlock.clearCache();
                com.railsounds.rail.RouteSensorBlock.clearCache();
                com.railsounds.rail.RailItems.clearCache();
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // One feature falling over mustn't take the others down with it, and it mustn't
            // write 20 stack traces a second either, so each gets its own guard and they all
            // share the same throttle.
            safely("shunting", () -> Shunting.tick(server));
            safely("engine sounds", () -> Engines.tick(server));
            safely("loco bell", () -> Bells.tick(server));
            safely("speed limits", () -> com.railsounds.rail.SpeedZones.tick(server));
            safely("train sounds", () -> {
                TrainSoundTracker.tick(server);
                Crossings.tick(server);
                SoundPlayer.tickScheduled(server);
                Horns.tick(server);
                if (server.getTickCount() % 1200 == 600) TrainTypes.cleanup();
            });
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            RailCommands.register(dispatcher);
            TypeCommands.register(dispatcher);
        });
        LOG.info("Create Rail Sounds loaded");
    }
}
