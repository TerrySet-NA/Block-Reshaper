package com.ts.br.util;

import com.mojang.logging.LogUtils;
import com.ts.br.block.GhostType;
import com.ts.br.config.ModConfig;
import com.ts.br.entity.GhostBlockEntity;
import com.ts.br.event.ModBlockEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PendingPlacementTracker {

    private static final Logger LOGGER = LogUtils.getLogger();

    private record PendingTask(
            ServerLevel level,
            BlockPos reportedPos,
            BlockState fallbackState,
            int expireTick
    ) {}

    private static final List<PendingTask> PENDING_TASKS = new ArrayList<>();

    public static void enqueue(ServerLevel level, BlockPos pos, BlockState expectedState) {
        PENDING_TASKS.add(new PendingTask(level, pos.immutable(), expectedState, 2));
    }

    public static void tick(ServerLevel level) {
        if (PENDING_TASKS.isEmpty()) return;

        Iterator<PendingTask> it = PENDING_TASKS.iterator();
        while (it.hasNext()) {
            PendingTask task = it.next();
            if (task.level() != level) continue;

            BlockPos actualPos = null;
            BlockState actualState = null;

            // 1. 檢查事件報告的座標本身是否已經成功落子（常規生物情境）
            BlockState stateAtReported = level.getBlockState(task.reportedPos());
            if (!stateAtReported.isAir() && !stateAtReported.canBeReplaced()) {
                actualPos = task.reportedPos();
                actualState = stateAtReported;
            }

            // 2. 如果報告座標依然是空氣，或者報告座標是地面（例如安德/特殊模組），
            // 檢查其上方格是否落子
            if (actualPos == null || stateAtReported.equals(task.fallbackState())) {
                BlockPos above = task.reportedPos().above();
                BlockState stateAbove = level.getBlockState(above);
                if (!stateAbove.isAir() && !stateAbove.canBeReplaced()) {
                    actualPos = above;
                    actualState = stateAbove;
                }
            }

            // 3. 檢查水平四周（應對側面懸掛或依附型放置）
            if (actualPos == null) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = task.reportedPos().relative(dir);
                    BlockState stateSide = level.getBlockState(side);
                    if (!stateSide.isAir() && !stateSide.canBeReplaced() && !GhostBlockTracker.isDisappearBlock(side)) {
                        actualPos = side;
                        actualState = stateSide;
                        break;
                    }
                }
            }

            // 找到了真實落子的座標！
            if (actualPos != null && actualState != null) {
                if (!ModBlockEvents.isNonRestorable(actualState)) {
                    if (GhostBlockTracker.isDisappearBlock(actualPos)) {
                        GhostBlockEntity old = GhostBlockTracker.getEntity(actualPos);
                        if (old != null && !old.isRemoved()) old.discard();
                        GhostBlockTracker.unregister(actualPos);
                    }

                    int disappearDelay = ModConfig.MONSTER_BLOCK_DISAPPEAR_DELAY.get();
                    GhostBlockEntity ghost = new GhostBlockEntity(
                            level,
                            actualPos,
                            actualState,
                            null,
                            disappearDelay,
                            GhostType.DISAPPEAR
                    );
                    level.addFreshEntity(ghost);
                    LOGGER.info("[BR] 通用落子追蹤確認！成功捕獲目標格={}, 方塊={}", actualPos, actualState);
                }
                it.remove();
                continue;
            }

            // 超時未發現任何方塊產生（放置被原版邏輯或其他插件攔截取消）
            if (task.expireTick() <= 0) {
                it.remove();
            }
        }
    }
}