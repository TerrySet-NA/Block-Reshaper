package com.ts.br.util;

import com.ts.br.entity.GhostBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GhostBlockTracker {

    private static final Map<BlockPos, GhostBlockEntity> DISAPPEAR_BLOCKS = new ConcurrentHashMap<>();

    private static final ThreadLocal<BlockPos> LAST_QUERIED_POS = new ThreadLocal<>();

    public static void setLastQueriedPos(BlockPos pos) {
        LAST_QUERIED_POS.set(pos);
    }

    public static BlockPos getLastQueriedPos() {
        return LAST_QUERIED_POS.get();
    }

    public static boolean isLastQueriedPosDisappear() {
        BlockPos pos = LAST_QUERIED_POS.get();
        return pos != null && isDisappearBlock(pos);
    }

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