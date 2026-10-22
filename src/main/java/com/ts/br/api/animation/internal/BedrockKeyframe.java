package com.ts.br.api.animation.internal;

import org.joml.Vector3f;

public class BedrockKeyframe {
    public final float time;
    public final Vector3f vector;
    public final String easing;
    public final float easingArg;

    public BedrockKeyframe(float time, Vector3f vector, String easing, float easingArg) {
        this.time = time;
        this.vector = vector;
        this.easing = easing != null ? easing : "linear";
        this.easingArg = easingArg;
    }

    public static float applyEasing(float t, String easing, float arg) {
        return switch (easing) {
            case "easeOutBack" -> {
                t = t - 1.0F;
                yield t * t * ((arg + 1.0F) * t + arg) + 1.0F;
            }
            case "easeInBack" -> t * t * ((arg + 1.0F) * t - arg);
            case "easeInOutBack" -> {
                float s2 = arg * 1.525F;
                t = t * 2.0F;
                if (t < 1.0F) {
                    yield 0.5F * (t * t * ((s2 + 1.0F) * t - s2));
                }
                t -= 2.0F;
                yield 0.5F * (t * t * ((s2 + 1.0F) * t + s2) + 2.0F);
            }
            case "easeInElastic" -> {
                if (t <= 0.0F) yield 0.0F;
                if (t >= 1.0F) yield 1.0F;
                float p = 0.3F;
                float s = (float) (p / (2.0F * Math.PI) * Math.asin(1.0F / Math.max(arg, 1.0F)));
                t -= 1.0F;
                yield -(float) (arg * Math.pow(2.0D, 10.0F * t) * Math.sin((t - s) * (2.0F * Math.PI) / p));
            }
            case "easeOutElastic" -> {
                if (t <= 0.0F) yield 0.0F;
                if (t >= 1.0F) yield 1.0F;
                float p = 0.3F;
                float s = (float) (p / (2.0F * Math.PI) * Math.asin(1.0F / Math.max(arg, 1.0F)));
                yield (float) (arg * Math.pow(2.0D, -10.0F * t) * Math.sin((t - s) * (2.0F * Math.PI) / p) + 1.0F);
            }
            default -> t; // linear
        };
    }
}