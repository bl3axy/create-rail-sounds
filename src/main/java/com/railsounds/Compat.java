package com.railsounds;

import com.zurrtum.create.content.trains.entity.Train;

import java.lang.reflect.Method;

/**
 * Optional Steam 'n' Rails hooks, done by reflection so the mod still works without S&R installed.
 * S&R adds a public railways$isHandcar() method onto Create's Train class via its mixin.
 */
public class Compat {
    private static boolean looked;
    private static Method isHandcar;

    public static boolean isHandcar(Train train) {
        if (!looked) {
            looked = true;
            try {
                isHandcar = Train.class.getMethod("railways$isHandcar");
            } catch (Throwable ignored) {
                isHandcar = null; // Steam 'n' Rails not installed
            }
        }
        if (isHandcar == null) return false;
        try {
            return (boolean) isHandcar.invoke(train);
        } catch (Throwable t) {
            return false;
        }
    }
}
