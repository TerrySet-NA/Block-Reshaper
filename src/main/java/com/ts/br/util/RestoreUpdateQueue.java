package com.ts.br.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RestoreUpdateQueue {

    private static final Map<ServerLevel, Set<BlockPos>> PENDING_UPDATES = new ConcurrentHashMap<>();

    public static void enqueue(ServerLevel level, BlockPos pos) {
        PENDING_UPDATES.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
    }

    /**
     * 第二階段：所有方塊皆已放回世界後，在 Tick 尾端統一執行形狀重新連通與物理更新
     */
    public static void flush(ServerLevel level) {
        Set<BlockPos> positions = PENDING_UPDATES.remove(level);
        if (positions == null || positions.isEmpty()) {
            return;
        }

        // 步驟 1：先發布所有已放置方塊的客戶端同步
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }

        // 步驟 2：核心統一形狀與鄰近更新
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);

            // 1. 常規物理與紅石更新
            level.updateNeighborsAt(pos, state.getBlock());

            // 2. 統一形狀更新：通知四周 6 個方向的相鄰方塊（包含倖存沒被炸毀的階梯/柵欄等）
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);

                BlockState updatedNeighbor = neighborState.updateShape(
                        dir.getOpposite(),
                        state,
                        level,
                        neighborPos,
                        pos
                );

                if (updatedNeighbor != neighborState) {
                    level.setBlock(neighborPos, updatedNeighbor, Block.UPDATE_ALL);
                }
            }
        }
    }
}