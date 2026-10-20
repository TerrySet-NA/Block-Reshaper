package com.ts.br.api.animation;

public interface IAnimatableState {
    String getCurrentAnimationName();
    float getAnimationTimeSeconds(float partialTick);
}