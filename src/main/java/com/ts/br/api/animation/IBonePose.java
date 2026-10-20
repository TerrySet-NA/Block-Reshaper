package com.ts.br.api.animation;

import org.joml.Vector3f;

public interface IBonePose {
    Vector3f getRotation();
    Vector3f getPosition();
    Vector3f getScale();
}