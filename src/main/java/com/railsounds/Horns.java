package com.railsounds;

import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Diesel horn / steam whistle for trains marked with /diesel or /steam.
 * Plays attached to the lead carriage so it follows the train.
 *
 * Players with this mod on their client get a long seamless "hold" clip that the
 * client fades out from wherever it is the moment you let go.
 * Players without it get the old way: the clip replays while held, then gets cut
 * and a short tail plays.
 *
 * If the release never arrives (left the seat while holding, lag) it releases
 * itself after a moment, so it can't get stuck on.
 */
public class Horns {
    public static final String DIESEL_HORN = "diesel_horn";
    public static final String STEAM_WHISTLE = "steam_whistle";

    private static class State {
        String sound;
        long lastKeepAlive;
        long nextReplay;
        ServerLevel level;
        CarriageContraptionEntity entity;
        /** modded players who were told to start the hold clip */
        final Set<UUID> modded = new HashSet<>();
    }

    private static final Map<UUID, State> HONKING = new HashMap<>();

    private static String soundFor(String type) {
        return TrainTypes.STEAM.equals(type) ? STEAM_WHISTLE : DIESEL_HORN;
    }

    private static int clipTicks(String sound) {
        return STEAM_WHISTLE.equals(sound) ? RailConfig.V.whistleClipTicks : RailConfig.V.hornClipTicks;
    }

    private static int fadeTicks(String sound) {
        return STEAM_WHISTLE.equals(sound) ? RailConfig.V.whistleFadeTicks : RailConfig.V.hornFadeTicks;
    }

    /** @return true if we handled it (typed train), so Create's own whistle should be skipped */
    public static boolean onHonk(MinecraftServer server, Train train, boolean pressed) {
        String type = TrainTypes.get(train);
        if (type == null) return false;
        long now = server.getTickCount();
        State s = HONKING.get(train.id);
        if (pressed) {
            if (s == null) {
                CarriageContraptionEntity e = train.carriages.isEmpty() ? null : train.carriages.get(0).anyAvailableEntity();
                if (e == null || !(e.level() instanceof ServerLevel level)) return true;
                s = new State();
                s.sound = soundFor(type);
                s.level = level;
                s.entity = e;
                HONKING.put(train.id, s);
                RailSounds.LOG.info("[railsounds] {} on for train {} ({} players nearby)", s.sound, train.name.getString(), nearby(s));
                startModded(s);
                playVanilla(s, s.sound);
                s.nextReplay = now + Math.max(5, clipTicks(s.sound));
            }
            s.lastKeepAlive = now;
        } else if (s != null) {
            RailSounds.LOG.info("[railsounds] {} off for train {} (released)", s.sound, train.name.getString());
            release(s);
            HONKING.remove(train.id);
        }
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (HONKING.isEmpty()) return;
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, State>> it = HONKING.entrySet().iterator();
        while (it.hasNext()) {
            State s = it.next().getValue();
            boolean gone = s.entity == null || s.entity.isRemoved();
            if (gone || now - s.lastKeepAlive > RailConfig.V.hornReleaseTimeoutTicks) {
                RailSounds.LOG.info("[railsounds] {} off ({})", s.sound, gone ? "train gone" : "no hold signal");
                release(s);
                it.remove();
                continue;
            }
            // modded players who just came into range join in; every 5s re-send to everyone so a
            // horn held longer than the hold clip restarts (clients ignore it while still playing)
            if (now % 100 == 0) resendModded(s);
            if (now % 5 == 0) startModded(s);
            if (now >= s.nextReplay) {
                playVanilla(s, s.sound);
                s.nextReplay = now + Math.max(5, clipTicks(s.sound));
            }
        }
    }

    public static void reset() {
        HONKING.clear();
    }

    private static boolean hasMod(ServerPlayer p) {
        try {
            return ServerPlayNetworking.canSend(p, HornPayload.TYPE);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean inRange(ServerPlayer p, State s, int range) {
        return p.distanceToSqr(s.entity) <= (double) range * range;
    }

    private static int nearby(State s) {
        int n = 0;
        for (ServerPlayer p : s.level.players()) if (inRange(p, s, RailConfig.V.hornHearingRange)) n++;
        return n;
    }

    private static Identifier id(String sound) {
        return Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, sound);
    }

    private static void startModded(State s) {
        if (s.entity == null || s.entity.isRemoved()) return;
        for (ServerPlayer p : s.level.players()) {
            if (s.modded.contains(p.getUUID()) || !inRange(p, s, RailConfig.V.hornHearingRange) || !hasMod(p)) continue;
            ServerPlayNetworking.send(p, new HornPayload(s.entity.getId(), s.sound + "_hold",
                RailConfig.V.hornVolume, true, 0));
            s.modded.add(p.getUUID());
        }
    }

    private static void resendModded(State s) {
        if (s.entity == null || s.entity.isRemoved()) return;
        for (ServerPlayer p : s.level.players()) {
            if (!s.modded.contains(p.getUUID())) continue;
            ServerPlayNetworking.send(p, new HornPayload(s.entity.getId(), s.sound + "_hold",
                RailConfig.V.hornVolume, true, 0));
        }
    }

    /** old-style sound for players without the mod */
    private static void playVanilla(State s, String sound) {
        if (s.entity == null || s.entity.isRemoved()) return;
        int range = RailConfig.V.hornHearingRange;
        Holder<SoundEvent> h = Holder.direct(SoundEvent.createFixedRangeEvent(id(sound), range));
        long seed = ThreadLocalRandom.current().nextLong();
        for (ServerPlayer p : s.level.players()) {
            if (!inRange(p, s, range) || hasMod(p)) continue;
            p.connection.send(new ClientboundSoundEntityPacket(h, SoundSource.NEUTRAL, s.entity,
                RailConfig.V.hornVolume, 1f, seed));
        }
    }

    private static void release(State s) {
        if (s.level == null) return;
        int entityId = s.entity == null ? -1 : s.entity.getId();
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(id(s.sound), SoundSource.NEUTRAL);
        int range = RailConfig.V.hornHearingRange + 32;
        for (ServerPlayer p : s.level.players()) {
            if (s.modded.contains(p.getUUID())) {
                // fade from right where it is now
                ServerPlayNetworking.send(p, new HornPayload(entityId, s.sound + "_hold", 0f, false, fadeTicks(s.sound)));
                continue;
            }
            if (s.entity != null && !s.entity.isRemoved() && !inRange(p, s, range)) continue;
            if (hasMod(p)) continue;
            p.connection.send(stop);
        }
        // vanilla players: short natural tail so letting go isn't a dead cut
        playVanilla(s, s.sound + "_end");
    }
}
