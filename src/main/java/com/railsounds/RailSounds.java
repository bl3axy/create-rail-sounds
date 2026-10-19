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
    public static final Logger LOG = LoggerFactory.getLogger("RailSounds");
    private static long lastErrorLog;

    @Override
    public void onInitialize() {
        RailConfig.load();
        Crossings.load();
        PayloadTypeRegistry.clientboundPlay().register(HornPayload.TYPE, HornPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShuntPayload.TYPE, ShuntPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AutoCouplePayload.TYPE, AutoCouplePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) -> Shunting.sendAutoState(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(ShuntPayload.TYPE, (p, ctx) -> {
            try {
                Shunting.onKey(ctx.player(), p.action(), p.number());
            } catch (RuntimeException e) {
                LOG.error("[railsounds] shunting key failed", e);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TrainSoundTracker.reset();
            TrainTypes.load(server);
            Shunting.reset();
            YardRules.load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SoundPlayer.clear();
            Crossings.onServerStop();
            TrainSoundTracker.reset();
            Horns.reset();
            TrainTypes.unload();
            Shunting.reset();
            YardRules.unload();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                Shunting.tick(server);
            } catch (Throwable t) {
                LOG.error("[railsounds] shunting tick failed", t);
            }
            try {
                TrainSoundTracker.tick(server);
                Crossings.tick(server);
                SoundPlayer.tickScheduled(server);
                Horns.tick(server);
                if (server.getTickCount() % 1200 == 600) TrainTypes.cleanup();
            } catch (Throwable t) {
                // never crash the server over a sound, and don't flood the log 20x a second either
                long now = System.currentTimeMillis();
                if (now - lastErrorLog > 60_000) {
                    lastErrorLog = now;
                    LOG.error("RailSounds tick failed (further errors hidden for 60s)", t);
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            RailCommands.register(dispatcher);
            TypeCommands.register(dispatcher);
        });
        LOG.info("Create Rail Sounds loaded");
    }
}
