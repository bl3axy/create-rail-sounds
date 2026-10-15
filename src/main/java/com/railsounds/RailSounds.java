package com.railsounds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

    @Override
    public void onInitialize() {
        RailConfig.load();
        Crossings.load();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> TrainSoundTracker.reset());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SoundPlayer.clear();
            Crossings.onServerStop();
            TrainSoundTracker.reset();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                TrainSoundTracker.tick(server);
                Crossings.tick(server);
                SoundPlayer.tickScheduled(server);
            } catch (Throwable t) {
                // never crash the server over a sound
                LOG.error("RailSounds tick failed", t);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> RailCommands.register(dispatcher));
        LOG.info("Create Rail Sounds loaded");
    }
}
