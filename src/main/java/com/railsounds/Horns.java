package com.railsounds;

import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
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
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Diesel horn / steam whistle for trains marked with /diesel or /steam.
 * Plays from the server, attached to the lead carriage so it follows the train.
 * Press = on straight away, release = cut straight away. If the release never
 * arrives (left the seat while holding, lag) it cuts itself after a moment,
 * so it can't get stuck on.
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
    }

    private static final Map<UUID, State> HONKING = new HashMap<>();

    private static String soundFor(String type) {
        return TrainTypes.STEAM.equals(type) ? STEAM_WHISTLE : DIESEL_HORN;
    }

    private static int clipTicks(String sound) {
        return STEAM_WHISTLE.equals(sound) ? RailConfig.V.whistleClipTicks : RailConfig.V.hornClipTicks;
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
                play(s);
                s.nextReplay = now + Math.max(5, clipTicks(s.sound));
            }
            s.lastKeepAlive = now;
        } else if (s != null) {
            stop(s);
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
                stop(s);
                it.remove();
            } else if (now >= s.nextReplay) {
                play(s);
                s.nextReplay = now + Math.max(5, clipTicks(s.sound));
            }
        }
    }

    public static void reset() {
        HONKING.clear();
    }

    private static Identifier id(String sound) {
        return Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, sound);
    }

    private static void play(State s) {
        int range = RailConfig.V.hornHearingRange;
        Holder<SoundEvent> h = Holder.direct(SoundEvent.createFixedRangeEvent(id(s.sound), range));
        double r2 = (double) range * range;
        long seed = ThreadLocalRandom.current().nextLong();
        for (ServerPlayer p : s.level.players()) {
            if (p.distanceToSqr(s.entity) > r2) continue;
            p.connection.send(new ClientboundSoundEntityPacket(h, SoundSource.NEUTRAL, s.entity,
                RailConfig.V.hornVolume, 1f, seed));
        }
    }

    private static void stop(State s) {
        if (s.level == null) return;
        ClientboundStopSoundPacket pkt = new ClientboundStopSoundPacket(id(s.sound), SoundSource.NEUTRAL);
        int range = RailConfig.V.hornHearingRange + 32;
        double r2 = (double) range * range;
        for (ServerPlayer p : s.level.players()) {
            if (s.entity != null && !s.entity.isRemoved() && p.distanceToSqr(s.entity) > r2) continue;
            p.connection.send(pkt);
        }
    }
}
