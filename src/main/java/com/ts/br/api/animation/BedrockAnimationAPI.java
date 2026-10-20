package com.ts.br.api.animation;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ts.br.api.animation.internal.BedrockAnimationLoader;
import com.ts.br.api.animation.internal.BedrockClip;
import com.ts.br.api.animation.internal.BedrockModel;
import com.ts.br.api.animation.internal.BlockTintRendererInternal;
import com.ts.br.api.animation.internal.BonePoseImpl;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public class BedrockAnimationAPI {

    public static void preload(ResourceLocation animationFile, ResourceLocation geoFile) {
        BedrockAnimationLoader.getClips(animationFile);
        BedrockModel.getOrLoad(geoFile);
    }

    public static void invalidateCache() {
        BedrockAnimationLoader.clearCache();
        BedrockModel.clearCache();
    }

    public static IBonePose createPoseHolder() {
        return new BonePoseImpl();
    }

    /**
     * 骨骼姿態單獨求值
     */
    public static void evaluateBone(ResourceLocation animationFile, String animationName, String boneName, float timeSeconds, IBonePose outPose) {
        if (!(outPose instanceof BonePoseImpl impl)) {
            throw new IllegalArgumentException("outPose must be created via BedrockAnimationAPI.createPoseHolder()");
        }

        BedrockClip clip = BedrockAnimationLoader.getClip(animationFile, animationName);
        if (clip == null) return;

        BedrockClip.BoneChannel channel = clip.boneChannels.get(boneName);
        if (channel == null) return;

        float clampedTime = Mth.clamp(timeSeconds, 0.0F, clip.length);
        channel.evalRotations(clampedTime, impl.getRotation());
        channel.evalPositions(clampedTime, impl.getPosition());
        channel.evalScales(clampedTime, impl.getScale());
    }

    /**
     * 外框幾何骨骼渲染
     */
    public static void renderAnimatedFrameByBone(ResourceLocation geoFile, ResourceLocation animationFile, String animationName,
                                                String frameBoneName, float timeSeconds, PoseStack poseStack,
                                                com.mojang.blaze3d.vertex.VertexConsumer consumer, int light,
                                                float r, float g, float b, float a) {
        BonePoseImpl pose = new BonePoseImpl();
        evaluateBone(animationFile, animationName, frameBoneName, timeSeconds, pose);

        if (pose.getScale().x <= 0.001F) return;

        poseStack.pushPose();
        poseStack.translate(pose.getPosition().x / 16.0F, 0.5F + (pose.getPosition().y / 16.0F), pose.getPosition().z / 16.0F);

        if (pose.getRotation().z != 0.0F) poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(pose.getRotation().z));
        if (pose.getRotation().y != 0.0F) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(pose.getRotation().y));
        if (pose.getRotation().x != 0.0F) poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pose.getRotation().x));

        poseStack.scale(pose.getScale().x, pose.getScale().y, pose.getScale().z);

        BedrockModel model = BedrockModel.getOrLoad(geoFile);
        if (model != null) {
            model.render(poseStack, consumer, light, r, g, b, a);
        }

        poseStack.popPose();
    }

    // ==================== 核心：通用方塊渲染建造者 ====================

    /**
     * 啟動方塊渲染配置流（支援多載與選擇性鏈式呼叫）
     */
    public static BlockRenderBuilder renderBlock(ResourceLocation animFile, String animName, String boneName, float timeSec) {
        return new BlockRenderBuilder(animFile, animName, boneName, timeSec);
    }

    public static class BlockRenderBuilder {
        private final ResourceLocation animFile;
        private final String animName;
        private final String boneName;
        private final float timeSec;

        // 選擇性參數（帶有安全預設值）
        private BlockAndTintGetter level = null;
        private BlockPos pos = BlockPos.ZERO;
        private boolean useSeed = true;
        private boolean useBiomeTint = true;
        private boolean alignCenter = true;
        private boolean flipSouthNorth = false;
        private int forcedColor = -1; // -1 表示使用生態域/原版預設，不強制覆蓋

        private BlockRenderBuilder(ResourceLocation animFile, String animName, String boneName, float timeSec) {
            this.animFile = animFile;
            this.animName = animName;
            this.boneName = boneName;
            this.timeSec = timeSec;
        }

        /**
         * 選擇性傳入：世界環境與座標（用於計算隨機貼圖種子與生態域顏色）
         */
        public BlockRenderBuilder at(BlockAndTintGetter level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
            return this;
        }

        /**
         * 選擇性設定：是否依座標套用隨機貼圖變體（預設 true）
         */
        public BlockRenderBuilder useSeed(boolean enable) {
            this.useSeed = enable;
            return this;
        }

        /**
         * 選擇性設定：是否啟用生態域群系著色（預設 true）
         */
        public BlockRenderBuilder useBiomeTint(boolean enable) {
            this.useBiomeTint = enable;
            return this;
        }

        /**
         * 選擇性設定：是否自動向中心對齊 (-0.5, -0.5, -0.5) 與翻轉 180 度（預設 true）
         */
        public BlockRenderBuilder alignGrid(boolean center, boolean flip) {
            this.alignCenter = center;
            this.flipSouthNorth = flip;
            return this;
        }

        /**
         * 選擇性設定：強制覆蓋 RGB 染色（如地圖代表色 0xRRGGBB）
         */
        public BlockRenderBuilder overrideColor(int rgb) {
            this.forcedColor = rgb;
            return this;
        }

        /**
         * 終結呼叫：執行繪製
         */
        public void draw(PoseStack poseStack, MultiBufferSource bufferSource, BlockState state, int light) {
            if (state == null || state.isAir()) return;

            BonePoseImpl pose = new BonePoseImpl();
            evaluateBone(this.animFile, this.animName, this.boneName, this.timeSec, pose);

            if (pose.getScale().x <= 0.001F || pose.getScale().y <= 0.001F || pose.getScale().z <= 0.001F) {
                return;
            }

            poseStack.pushPose();

            Vector3f bonePos = pose.getPosition();
            Vector3f rot = pose.getRotation();
            Vector3f scale = pose.getScale();

            // 1. 移動至方塊中心 (Y = 0.5) 並疊加骨骼動畫位移
            poseStack.translate(bonePos.x / 16.0F, 0.5F + (bonePos.y / 16.0F), bonePos.z / 16.0F);

            // 2. 套用 Blockbench 標準旋轉 (ZYX 順序)
            // 注意：若動畫中 X 軸旋轉與預期相反，Blockbench 的 X 旋轉在 Minecraft 需保持負正對齊
            if (rot.z != 0.0F) poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot.z));
            if (rot.y != 0.0F) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rot.y));
            if (rot.x != 0.0F) poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rot.x));

            // 3. 核心修正：移除原先強制寫死的 180 度翻轉！
            // 只有當外部明確指定要翻轉時才翻轉，預設不翻轉以保持原版 BlockState 朝向
            if (this.flipSouthNorth) {
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
            }

            // 4. 縮放
            poseStack.scale(scale.x, scale.y, scale.z);

            // 5. 原版方塊以 (-0.5, -0.5, -0.5) 置中對齊
            if (this.alignCenter) {
                poseStack.translate(-0.5, -0.5, -0.5);
            }

            // 6. 繪製方塊
            BlockTintRendererInternal.render(
                    poseStack, bufferSource, state, this.level, this.pos,
                    light, this.useSeed, this.useBiomeTint, this.forcedColor
            );

            poseStack.popPose();
        }
    }
}