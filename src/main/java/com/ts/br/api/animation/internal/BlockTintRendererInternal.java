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
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BlockTintRendererInternal {

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, BlockState state,
                              BlockAndTintGetter level, BlockPos pos, int packedLight,
                              boolean useSeed, boolean useBiomeTint, int forcedColor, CompoundTag tileTag) {

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        RenderShape renderShape = state.getRenderShape();

        // 核心：還原花草、竹筍等植物的隨機自然模型偏移 (Model Offset)
        poseStack.pushPose();
        if (pos != null && level != null) {
            Vec3 offset = state.getOffset(level, pos);
            if (offset != Vec3.ZERO) {
                poseStack.translate(offset.x, offset.y, offset.z);
            }
        }

        try {
            // 1. 純實體動畫方塊 (如普通箱子、界伏盒：無靜態模型)
            if (renderShape == RenderShape.ENTITYBLOCK_ANIMATED) {
                renderSpecialBlockEntity(poseStack, bufferSource, state, pos, packedLight, tileTag, mc);
                return;
            }

            // 2. 繪製靜態模型層 (鐘的支架、一般石頭木板、草花植被等)
            if (renderShape == RenderShape.MODEL) {
                BakedModel model = dispatcher.getBlockModel(state);
                BlockColors blockColors = mc.getBlockColors();

                RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
                VertexConsumer consumer = bufferSource.getBuffer(renderType);

                PoseStack.Pose entry = poseStack.last();
                long seed = (useSeed && pos != null) ? state.getSeed(pos) : 42L;
                RandomSource random = RandomSource.create(seed);

                for (Direction direction : Direction.values()) {
                    random.setSeed(seed);
                    List<BakedQuad> quads = model.getQuads(state, direction, random);
                    if (!quads.isEmpty()) {
                        renderQuads(entry, consumer, quads, state, level, pos, blockColors, packedLight, useBiomeTint, forcedColor);
                    }
                }

                random.setSeed(seed);
                List<BakedQuad> unculled = model.getQuads(state, null, random);
                if (!unculled.isEmpty()) {
                    renderQuads(entry, consumer, unculled, state, level, pos, blockColors, packedLight, useBiomeTint, forcedColor);
                }
            }

            // 3. 混合型方塊實體 (例如「鐘」：支架畫完了，追加繪製中間的動態鈴鐺)
            if (state.getBlock() instanceof EntityBlock) {
                renderSpecialBlockEntity(poseStack, bufferSource, state, pos, packedLight, tileTag, mc);
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 動態方塊實體繪製 (鐘的鈴鐺、箱子開闔、告示牌等)
     */
    private static void renderSpecialBlockEntity(PoseStack poseStack, MultiBufferSource bufferSource,
                                                 BlockState state, BlockPos pos, int packedLight,
                                                 CompoundTag tileTag, Minecraft mc) {
        if (mc.level == null) return;

        BlockPos validPos = (pos != null) ? pos : BlockPos.ZERO;
        BlockEntity targetBe = null;

        if (tileTag != null) {
            targetBe = BlockEntity.loadStatic(validPos, state, tileTag, mc.level.registryAccess());
        }

        if (targetBe == null && state.getBlock() instanceof EntityBlock entityBlock) {
            targetBe = entityBlock.newBlockEntity(validPos, state);
        }

        if (targetBe != null) {
            targetBe.setLevel(mc.level);
            
            BlockEntityRenderDispatcher beDispatcher = mc.getBlockEntityRenderDispatcher();
            try {
                beDispatcher.render(targetBe, 0.0F, poseStack, bufferSource);
                return;
            } catch (Exception ignored) {
                // 發生非預期例外時切換到保底管線
            }
        }

        // 物品管線保底 (在純靜態展示時有效)
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (!stack.isEmpty() && state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 0.5D, 0.5D);
            mc.getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.NONE,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    mc.level,
                    0
            );
            poseStack.popPose();
        }
    }

    private static void renderQuads(PoseStack.Pose entry, VertexConsumer consumer, List<BakedQuad> quads,
                                    BlockState state, BlockAndTintGetter level, BlockPos pos,
                                    BlockColors blockColors, int light, boolean useBiomeTint, int forcedColor) {
        for (BakedQuad quad : quads) {
            float r = 1.0F;
            float g = 1.0F;
            float b = 1.0F;

            if (forcedColor != -1) {
                r = (float) ((forcedColor >> 16) & 0xFF) / 255.0F;
                g = (float) ((forcedColor >> 8) & 0xFF) / 255.0F;
                b = (float) (forcedColor & 0xFF) / 255.0F;
            } else if (useBiomeTint && quad.isTinted() && level != null && pos != null) {
                int color = blockColors.getColor(state, level, pos, quad.getTintIndex());
                r = (float) ((color >> 16) & 0xFF) / 255.0F;
                g = (float) ((color >> 8) & 0xFF) / 255.0F;
                b = (float) (color & 0xFF) / 255.0F;
            }

            consumer.putBulkData(entry, quad, r, g, b, 1.0F, light, OverlayTexture.NO_OVERLAY);
        }
    }
}