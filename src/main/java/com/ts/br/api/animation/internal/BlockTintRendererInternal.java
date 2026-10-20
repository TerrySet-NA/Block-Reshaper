package com.ts.br.api.animation.internal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class BlockTintRendererInternal {

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, BlockState state,
                              BlockAndTintGetter level, BlockPos pos, int packedLight,
                              boolean useSeed, boolean useBiomeTint, int forcedColor) {

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(state);
        BlockColors blockColors = Minecraft.getInstance().getBlockColors();

        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        PoseStack.Pose entry = poseStack.last();

        long seed = (useSeed && pos != null) ? state.getSeed(pos) : 42L;
        RandomSource random = RandomSource.create(seed);

        for (Direction direction : Direction.values()) {
            random.setSeed(seed);
            List<BakedQuad> quads = model.getQuads(state, direction, random);
            renderQuads(entry, consumer, quads, state, level, pos, blockColors, packedLight, useBiomeTint, forcedColor);
        }

        random.setSeed(seed);
        List<BakedQuad> unculled = model.getQuads(state, null, random);
        renderQuads(entry, consumer, unculled, state, level, pos, blockColors, packedLight, useBiomeTint, forcedColor);
    }

    private static void renderQuads(PoseStack.Pose entry, VertexConsumer consumer, List<BakedQuad> quads,
                                    BlockState state, BlockAndTintGetter level, BlockPos pos,
                                    BlockColors blockColors, int light, boolean useBiomeTint, int forcedColor) {
        for (BakedQuad quad : quads) {
            float r = 1.0F;
            float g = 1.0F;
            float b = 1.0F;

            if (forcedColor != -1) {
                // 強制覆蓋染色
                r = (float) ((forcedColor >> 16) & 0xFF) / 255.0F;
                g = (float) ((forcedColor >> 8) & 0xFF) / 255.0F;
                b = (float) (forcedColor & 0xFF) / 255.0F;
            } else if (useBiomeTint && quad.isTinted() && level != null && pos != null) {
                // 生態域色彩查詢
                int color = blockColors.getColor(state, level, pos, quad.getTintIndex());
                r = (float) ((color >> 16) & 0xFF) / 255.0F;
                g = (float) ((color >> 8) & 0xFF) / 255.0F;
                b = (float) (color & 0xFF) / 255.0F;
            }

            consumer.putBulkData(entry, quad, r, g, b, 1.0F, light, OverlayTexture.NO_OVERLAY);
        }
    }
}