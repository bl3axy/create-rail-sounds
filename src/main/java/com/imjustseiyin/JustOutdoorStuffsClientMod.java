package com.imjustseiyin;

import com.imjustseiyin.block.ModBlocks;
import com.imjustseiyin.sit.SitEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.Block;

public class JustOutdoorStuffsClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(JustOutdoorStuffsMod.SIT_ENTITY_TYPE, SitRenderer::new);

        for (Block b : new Block[]{
            ModBlocks.GARDEN_CHAIR, ModBlocks.GARDEN_STOOL, ModBlocks.GARDEN_BENCH,
            ModBlocks.GARDEN_TABLE_SMALL, ModBlocks.GARDEN_TABLE_WIDE, ModBlocks.GARDEN_TABLE_LARGE,
            ModBlocks.PATIO_IRON_CHAIR, ModBlocks.PATIO_IRON_STOOL, ModBlocks.PATIO_IRON_BENCH,
            ModBlocks.PATIO_IRON_TABLE_SMALL, ModBlocks.PATIO_IRON_TABLE_WIDE, ModBlocks.PATIO_IRON_TABLE_LARGE,
            ModBlocks.PATIO_IRON_ROCKING_CHAIR, ModBlocks.PATIO_BBQ_GRILL, ModBlocks.PATIO_COFFEE_TABLE_GLASS
        }) {
            BlockRenderLayerMap.putBlock(b, ChunkSectionLayer.CUTOUT);
        }
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
