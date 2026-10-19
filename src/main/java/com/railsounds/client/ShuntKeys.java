package com.railsounds.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.railsounds.RailSounds;
import com.railsounds.ShuntPayload;
import com.railsounds.shunt.Shunting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Shunting keys (all rebindable under Controls):
 *   G          couple to the cut your train is touching (or the coupling you look at, on foot)
 *   U + 1..9   uncouple at that coupling, counted from the car you're in
 *   U (tap)    on foot: uncouple the coupling you're looking at (in a train it shows the hint)
 *   H          toggle auto-couple for trains you drive
 * While U is held the number keys don't change your hotbar slot.
 */
public final class ShuntKeys {
    private ShuntKeys() {}

    private static KeyMapping couple, uncouple, toggleAuto;
    private static boolean uncoupleWasDown, numberUsed;

    public static void register() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "shunting"));
        couple = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.railsounds.couple", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, cat));
        uncouple = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.railsounds.uncouple", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, cat));
        toggleAuto = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.railsounds.toggle_autocouple", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, cat));

        // START of the tick: grab number presses before vanilla turns them into hotbar switches
        ClientTickEvents.START_CLIENT_TICK.register(ShuntKeys::numbers);
        ClientTickEvents.END_CLIENT_TICK.register(ShuntKeys::others);
    }

    private static boolean canSend() {
        return ClientPlayNetworking.canSend(ShuntPayload.TYPE);
    }

    private static void send(int action, int n) {
        if (canSend()) ClientPlayNetworking.send(new ShuntPayload(action, n));
    }

    private static void numbers(Minecraft mc) {
        if (mc.player == null || mc.gui.screen() != null) {
            uncoupleWasDown = false;
            return;
        }
        boolean down = uncouple.isDown();
        if (down && !uncoupleWasDown) {
            numberUsed = false;
            if (mc.player.getVehicle() != null) send(Shunting.HINT, 0);
        }
        if (down) {
            KeyMapping[] slots = mc.options.keyHotbarSlots;
            for (int i = 0; i < slots.length && i < 9; i++) {
                while (slots[i].consumeClick()) {
                    numberUsed = true;
                    send(Shunting.UNCOUPLE, i + 1);
                }
            }
        } else if (uncoupleWasDown && !numberUsed && mc.player.getVehicle() == null) {
            // tapped on its own while on foot: the coupling under the crosshair
            send(Shunting.UNCOUPLE_LOOK, 0);
        }
        uncoupleWasDown = down;
        while (uncouple.consumeClick()) {
            // presses are handled through isDown above; just drain the counter
        }
    }

    private static void others(Minecraft mc) {
        if (mc.player == null) return;
        while (couple.consumeClick()) send(Shunting.COUPLE, 0);
        while (toggleAuto.consumeClick()) send(Shunting.TOGGLE_AUTO, 0);
    }
}
