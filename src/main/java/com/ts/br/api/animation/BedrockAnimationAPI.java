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
import net.minecraft.nbt.CompoundTag;
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

    public static BlockRenderBuilder renderBlock(ResourceLocation animFile, String animName, String boneName, float timeSec) {
        return new BlockRenderBuilder(animFile, animName, boneName, timeSec);
    }

    public static class BlockRenderBuilder {
        private final ResourceLocation animFile;
        private final String animName;
        private final String boneName;
        private final float timeSec;

        // 選擇性參數
        private BlockAndTintGetter level = null;
        private BlockPos pos = BlockPos.ZERO;
        private CompoundTag tileTag = null; // 支援方塊實體渲染的資料標籤
        private boolean useSeed = true;
        private boolean useBiomeTint = true;
        private boolean alignCenter = true;
        private boolean flipSouthNorth = false;
        private int forcedColor = -1;

        private BlockRenderBuilder(ResourceLocation animFile, String animName, String boneName, float timeSec) {
            this.animFile = animFile;
            this.animName = animName;
            this.boneName = boneName;
            this.timeSec = timeSec;
        }

        public BlockRenderBuilder at(BlockAndTintGetter level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
            return this;
        }

        /**
         * 選擇性傳入：方塊實體 NBT 資料（使箱子、界伏盒、床等特殊方塊能正確渲染模型與朝向）
         */
        public BlockRenderBuilder withTileTag(CompoundTag tileTag) {
            this.tileTag = tileTag;
            return this;
        }

        public BlockRenderBuilder useSeed(boolean enable) {
            this.useSeed = enable;
            return this;
        }

        public BlockRenderBuilder useBiomeTint(boolean enable) {
            this.useBiomeTint = enable;
            return this;
        }

        public BlockRenderBuilder alignGrid(boolean center, boolean flip) {
            this.alignCenter = center;
            this.flipSouthNorth = flip;
            return this;
        }

        public BlockRenderBuilder overrideColor(int rgb) {
            this.forcedColor = rgb;
            return this;
        }

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

            // 1. 移動至中心
            poseStack.translate(bonePos.x / 16.0F, 0.5F + (bonePos.y / 16.0F), bonePos.z / 16.0F);

            // 2. 旋轉
            if (rot.z != 0.0F) poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot.z));
            if (rot.y != 0.0F) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rot.y));
            if (rot.x != 0.0F) poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rot.x));

            if (this.flipSouthNorth) {
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
            }

            // 3. 縮放
            poseStack.scale(scale.x, scale.y, scale.z);

            // 4. 置中原點 (-0.5, -0.5, -0.5)
            if (this.alignCenter) {
                poseStack.translate(-0.5, -0.5, -0.5);
            }

            // 5. 渲染
            BlockTintRendererInternal.render(
                    poseStack, bufferSource, state, this.level, this.pos,
                    light, this.useSeed, this.useBiomeTint, this.forcedColor, this.tileTag
            );

            poseStack.popPose();
        }
    }
}