package com.ts.br.block;

import net.minecraft.util.StringRepresentable;

public enum GhostType implements StringRepresentable {
    RESTORE("restore"),
    DISAPPEAR("disappear");

    private final String name;

    GhostType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}