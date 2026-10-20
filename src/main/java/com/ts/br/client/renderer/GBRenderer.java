package com.ts.br.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ts.br.api.animation.BedrockAnimationAPI;
import com.ts.br.block.GhostType;
import com.ts.br.entity.GhostBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class GBRenderer extends EntityRenderer<GhostBlockEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("br", "textures/entity/gb.png");
    private static final ResourceLocation GEO_JSON = ResourceLocation.fromNamespaceAndPath("br", "geo/gb.geo.json");
    private static final ResourceLocation ANIM_JSON = ResourceLocation.fromNamespaceAndPath("br", "animations/gbca.animation.json");

    // 骨架名稱直接注入 API
    public static final String BONE_CENTER_BLOCK = "bone6";
    public static final String BONE_FRAME = "bone7";

    public GBRenderer(EntityRendererProvider.Context context) {
        super(context);
        BedrockAnimationAPI.preload(ANIM_JSON, GEO_JSON);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostBlockEntity entity) {
        return TEXTURE;
    }

    private int getFacingMaxLight(GhostBlockEntity entity) {
        Level level = entity.level();
        if (level == null) return 0xF000F0;

        BlockPos centerPos = entity.getTargetPos();
        int maxBlockLight = 0;
        int maxSkyLight = 0;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = centerPos.relative(dir);
            int light = LevelRenderer.getLightColor(level, neighborPos);

            int block = light & 0xFFFF;
            int sky = (light >> 16) & 0xFFFF;

            if (block > maxBlockLight) maxBlockLight = block;
            if (sky > maxSkyLight) maxSkyLight = sky;
        }

        if (maxBlockLight == 0 && maxSkyLight == 0) {
            return LevelRenderer.getLightColor(level, centerPos);
        }

        return (maxSkyLight << 16) | maxBlockLight;
    }

    @Override
    public void render(GhostBlockEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 消失型在未開始主階段前，地上是原版真方塊，實體靜默不渲染
        if (entity.getGhostType() == GhostType.DISAPPEAR && !entity.isMainAnimStarted()) {
            return;
        }

        poseStack.pushPose();

        String animName = entity.getCurrentAnimationName();
        float animTime = entity.getAnimationTimeSeconds(partialTick);

        int light = getFacingMaxLight(entity);
        int tint = entity.getTintColor();
        float r = ((tint >> 16) & 0xFF) / 255.0F;
        float g = ((tint >> 8) & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;

        // 1. 外框骨骼渲染 (bone7)
        VertexConsumer frameConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
        BedrockAnimationAPI.renderAnimatedFrameByBone(
                GEO_JSON, ANIM_JSON, animName, BONE_FRAME, animTime,
                poseStack, frameConsumer, light, r, g, b, 1.0F
        );

        // 2. 中央方塊渲染 (bone6)
        // 完全不使用 stage 判定！直接由動畫 JSON 的 scale 決定顯示與縮放
        BlockState originalState = entity.getOriginalState();
        BedrockAnimationAPI.renderBlock(ANIM_JSON, animName, BONE_CENTER_BLOCK, animTime)
                .at(entity.level(), entity.getTargetPos())
                .draw(poseStack, bufferSource, originalState, light);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}