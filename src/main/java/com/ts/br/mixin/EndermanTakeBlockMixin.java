package com.ts.br.mixin;

import com.ts.br.block.GhostType;
import com.ts.br.config.ModConfig;
import com.ts.br.entity.GhostBlockEntity;
import com.ts.br.event.ModBlockEvents;
import com.ts.br.util.ContainerHelper;
import com.ts.br.util.EntityRestoreFilter;
import com.ts.br.util.GhostBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockMixin {

    @Shadow @Final private EnderMan enderman;

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
        )
    )
    private boolean br$interceptEndermanTakeBlock(Level level, BlockPos pos, boolean isMoving) {
        if (ModConfig.ENABLED.get() && level instanceof ServerLevel serverLevel) {
            if (serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {

                if (EntityRestoreFilter.shouldRestoreFor(this.enderman)) {
                    // 若安德搬走的是怪物放的暫態方塊，直接註銷實體
                    if (GhostBlockTracker.isDisappearBlock(pos)) {
                        GhostBlockEntity ghost = GhostBlockTracker.getEntity(pos);
                        if (ghost != null && !ghost.isRemoved()) {
                            ghost.discard();
                        }
                        GhostBlockTracker.unregister(pos);
                    } else {
                        BlockState state = serverLevel.getBlockState(pos);
                        if (!ModBlockEvents.isNonRestorable(state)) {
                            // 錄入時直接去掉 SNOWY 狀態
                            BlockState cleanState = ModBlockEvents.sanitizeStateForCapture(state);

                            ContainerHelper.BlockDataSnapshot snapshot = ContainerHelper.captureAndClearBlockEntity(serverLevel, pos);
                            CompoundTag tileTag = snapshot != null ? snapshot.nbt() : null;

                            int restoreDelay = ModConfig.RESTORE_DELAY_TICKS.get();
                            GhostBlockEntity ghost = new GhostBlockEntity(serverLevel, pos, cleanState, tileTag, restoreDelay, GhostType.RESTORE);
                            serverLevel.addFreshEntity(ghost);
                        }
                    }
                }
            }
        }
        return level.removeBlock(pos, isMoving);
    }
}