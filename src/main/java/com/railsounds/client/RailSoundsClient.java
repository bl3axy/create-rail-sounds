package com.railsounds.client;

import com.railsounds.HornPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/** Client half: plays held horns/whistles the server tells us about, and fades them on release. */
public class RailSoundsClient implements ClientModInitializer {
    private static final Map<Integer, HornSound> ACTIVE = new HashMap<>();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(HornPayload.TYPE, (p, ctx) -> handle(ctx.client(), p));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIVE.clear());
        ClientPlayNetworking.registerGlobalReceiver(com.railsounds.AutoCouplePayload.TYPE, (p, ctx) -> FacingHud.autoCoupleOn = p.on());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FacingHud.autoCoupleOn = true);
        ShuntKeys.register();
        FacingHud.register();
    }

    private static void handle(Minecraft mc, HornPayload p) {
        // gone if we stopped it, or the clip ended / got culled by the sound engine on its own
        ACTIVE.values().removeIf(h -> h.isStopped() || (h.ageMillis() > 1000 && !mc.getSoundManager().isActive(h)));
        HornSound current = ACTIVE.get(p.entityId());
        if (p.start()) {
            if (current != null && !current.isFading()) return; // already blowing
            if (current != null) current.cut();
            if (mc.level == null) return;
            Entity e = mc.level.getEntity(p.entityId());
            if (e == null) return;
            HornSound s = new HornSound(p.sound(), e, p.volume());
            ACTIVE.put(p.entityId(), s);
            mc.getSoundManager().play(s);
        } else if (current != null) {
            current.fadeOut(p.fadeTicks());
        }
    }
}
