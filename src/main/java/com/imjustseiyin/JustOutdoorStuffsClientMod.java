package com.imjustseiyin;

import com.imjustseiyin.sit.SitEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public class JustOutdoorStuffsClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(JustOutdoorStuffsMod.SIT_ENTITY_TYPE, SitRenderer::new);

        // 26.2 picks cutout/translucent rendering from the textures, so no render layer setup is needed
    }

    /** The seat is invisible. */
    private static class SitRenderer extends EntityRenderer<SitEntity, EntityRenderState> {
        SitRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public boolean shouldRender(SitEntity entity, Frustum frustum, double x, double y, double z) {
            return false;
        }

        @Override
        public EntityRenderState createRenderState() {
            return new EntityRenderState();
        }

        @Override
        public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        }
    }
}
