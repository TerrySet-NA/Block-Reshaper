package com.ts.br.api.animation.internal;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.*;

public class BedrockClip {
    public final float length;
    public final Map<String, BoneChannel> boneChannels = new HashMap<>();

    public BedrockClip(float length) {
        this.length = length;
    }

    public static class BoneChannel {
        public final List<BedrockKeyframe> rotations = new ArrayList<>();
        public final List<BedrockKeyframe> positions = new ArrayList<>();
        public final List<BedrockKeyframe> scales = new ArrayList<>();

        public void evalRotations(float time, Vector3f dest) {
            eval(this.rotations, time, dest, 0.0F, 0.0F, 0.0F);
        }

        public void evalPositions(float time, Vector3f dest) {
            eval(this.positions, time, dest, 0.0F, 0.0F, 0.0F);
        }

        public void evalScales(float time, Vector3f dest) {
            eval(this.scales, time, dest, 1.0F, 1.0F, 1.0F);
        }

        private void eval(List<BedrockKeyframe> kfs, float time, Vector3f dest, float defX, float defY, float defZ) {
            if (kfs.isEmpty()) {
                dest.set(defX, defY, defZ);
                return;
            }
            if (time <= kfs.get(0).time) {
                dest.set(kfs.get(0).vector);
                return;
            }
            if (time >= kfs.get(kfs.size() - 1).time) {
                dest.set(kfs.get(kfs.size() - 1).vector);
                return;
            }

            for (int i = 0; i < kfs.size() - 1; i++) {
                BedrockKeyframe k1 = kfs.get(i);
                BedrockKeyframe k2 = kfs.get(i + 1);
                if (time >= k1.time && time <= k2.time) {
                    float t = (time - k1.time) / (k2.time - k1.time);
                    float easedT = BedrockKeyframe.applyEasing(t, k2.easing, k2.easingArg);
                    dest.set(
                            Mth.lerp(easedT, k1.vector.x, k2.vector.x),
                            Mth.lerp(easedT, k1.vector.y, k2.vector.y),
                            Mth.lerp(easedT, k1.vector.z, k2.vector.z)
                    );
                    return;
                }
            }
        }
    }
}