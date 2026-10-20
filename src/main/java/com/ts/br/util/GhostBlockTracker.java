package com.ts.br.util;

import com.ts.br.entity.GhostBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GhostBlockTracker {

    private static final Map<BlockPos, GhostBlockEntity> DISAPPEAR_BLOCKS = new ConcurrentHashMap<>();

    public static void register(BlockPos pos, GhostBlockEntity entity) {
        DISAPPEAR_BLOCKS.put(pos.immutable(), entity);
    }

    public static void unregister(BlockPos pos) {
        DISAPPEAR_BLOCKS.remove(pos);
    }

    public static boolean isDisappearBlock(BlockPos pos) {
        return DISAPPEAR_BLOCKS.containsKey(pos);
    }

    public static GhostBlockEntity getEntity(BlockPos pos) {
        return DISAPPEAR_BLOCKS.get(pos);
    }
}