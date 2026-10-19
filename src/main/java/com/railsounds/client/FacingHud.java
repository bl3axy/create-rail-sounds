package com.railsounds.client;

import com.railsounds.RailSounds;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Small box in the top-left corner while you look at a train (or ride one):
 * an arrow pointing at that train's FRONT, its name, and - if you're riding a train and
 * looking at another - whether the two face the same way (only then can they couple).
 */
public final class FacingHud {
    private FacingHud() {}

    private static final double RANGE = 24;
    private static final int BG = 0x90000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, "train_facing"), FacingHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            draw(mc, g);
        } catch (RuntimeException ignored) {
            // never break the HUD over this
        }
    }

    private static void draw(Minecraft mc, GuiGraphicsExtractor g) {
        Train mine = mc.player.getVehicle() instanceof CarriageContraptionEntity e ? trainOf(e) : null;
        Train looked = lookedAt(mc, mine);
        Train shown = looked != null ? looked : mine;
        if (shown == null) return;
        Vec3 front = frontDir(shown);
        if (front == null) return;

        Font font = mc.font;
        String name = shown.name == null ? "Train" : shown.name.getString();
        if (name.length() > 22) name = name.substring(0, 21) + "…";
        String label = (shown == mine ? "Your train: " : "") + name;

        String line2 = null;
        int color2 = WHITE;
        if (mine != null && looked != null && looked != mine) {
            Vec3 myFront = frontDir(mine);
            if (myFront != null) {
                boolean same = myFront.x * front.x + myFront.z * front.z > 0;
                line2 = same ? "✔ Same way - can couple" : "✘ Facing other way - can't couple";
                color2 = same ? GREEN : RED;
            }
        }

        int x = 6, y = 6, arrowBox = 18;
        int textX = x + arrowBox + 4;
        int w = Math.max(font.width("Front ▸ " + label), line2 == null ? 0 : font.width(line2)) + arrowBox + 10;
        int h = line2 == null ? arrowBox + 4 : arrowBox + 14;
        g.fill(x - 3, y - 3, x - 3 + w, y - 3 + h, BG);

        // arrow pointing where the train's front is, relative to where you're looking
        float frontYaw = (float) Math.toDegrees(Math.atan2(-front.x, front.z));
        float rel = (float) Math.toRadians(frontYaw - mc.player.getYRot());
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + arrowBox / 2f, y + arrowBox / 2f);
        pose.rotate(rel);
        pose.scale(1.6f, 1.6f);
        int aw = font.width("↑");
        g.text(font, "↑", -aw / 2, -4, 0xFFFFD24A, true);
        pose.popMatrix();

        g.text(font, Component.literal("Front"), textX, y + 1, 0xFFFFD24A, true);
        g.text(font, Component.literal(label), textX, y + 10, GRAY, true);
        if (line2 != null) g.text(font, Component.literal(line2), x, y + arrowBox + 3, color2, true);
    }

    private static Train trainOf(CarriageContraptionEntity e) {
        if (e.trainId == null || com.zurrtum.create.client.Create.RAILWAYS == null) return null;
        return com.zurrtum.create.client.Create.RAILWAYS.trains.get(e.trainId);
    }

    /** nearest train car under the crosshair (not your own train) */
    private static Train lookedAt(Minecraft mc, Train mine) {
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        Vec3 eye = cam.getEyePosition();
        Vec3 end = eye.add(cam.getViewVector(1f).scale(RANGE));
        AABB area = new AABB(eye, end).inflate(8);
        Train best = null;
        double bestDist = Double.MAX_VALUE;
        for (CarriageContraptionEntity e : mc.level.getEntitiesOfClass(CarriageContraptionEntity.class, area)) {
            Train t = trainOf(e);
            if (t == null || t == mine) continue;
            Optional<Vec3> hit = e.getBoundingBox().clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    /** horizontal direction the train's front points (from its lead car's front to its last car's back) */
    static Vec3 frontDir(Train t) {
        if (t.carriages.isEmpty()) return null;
        Minecraft mc = Minecraft.getInstance();
        Carriage first = t.carriages.get(0);
        Carriage last = t.carriages.get(t.carriages.size() - 1);
        Carriage.DimensionalCarriageEntity a = first.getDimensionalIfPresent(mc.level.dimension());
        Carriage.DimensionalCarriageEntity b = last.getDimensionalIfPresent(mc.level.dimension());
        if (a == null || b == null || a.rotationAnchors == null || b.rotationAnchors == null) return null;
        Vec3 lead = a.rotationAnchors.getFirst();
        Vec3 trail = b.rotationAnchors.getSecond();
        if (lead == null || trail == null) return null;
        Vec3 d = new Vec3(lead.x - trail.x, 0, lead.z - trail.z);
        return d.lengthSqr() < 1e-4 ? null : d.normalize();
    }
}
