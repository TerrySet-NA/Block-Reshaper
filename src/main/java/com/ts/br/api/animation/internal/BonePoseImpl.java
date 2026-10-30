package com.ts.br.api.animation.internal;

import com.ts.br.api.animation.IBonePose;
import org.joml.Vector3f;

public class BonePoseImpl implements IBonePose {
    private final Vector3f rotation = new Vector3f(0.0F, 0.0F, 0.0F);
    private final Vector3f position = new Vector3f(0.0F, 0.0F, 0.0F);
    private final Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F);

    @Override
    public Vector3f getRotation() {
        return this.rotation;
    }

    @Override
    public Vector3f getPosition() {
        return this.position;
    }

    @Override
    public Vector3f getScale() {
        return this.scale;
    }
}