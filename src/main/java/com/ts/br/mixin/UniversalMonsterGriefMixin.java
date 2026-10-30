package com.ts.br.mixin;

import com.ts.br.block.GhostType;
import com.ts.br.config.ModConfig;
import com.ts.br.entity.GhostBlockEntity;
import com.ts.br.event.ModBlockEvents;
import com.ts.br.util.ContainerHelper;
import com.ts.br.util.EntityRestoreFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class UniversalMonsterGriefMixin {

    @Inject(
        method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void br$onUniversalMobDestroyBlock(BlockPos pos, boolean dropResources, @Nullable Entity entity, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;

        if (!ModConfig.ENABLED.get()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;

        // 接駁黑白名單核心
        if (EntityRestoreFilter.shouldRestoreFor(entity)) {
            BlockState state = serverLevel.getBlockState(pos);

            if (!state.isAir() && !ModBlockEvents.isNonRestorable(state)) {
                // 錄入時直接去掉 SNOWY 狀態
                BlockState cleanState = ModBlockEvents.sanitizeStateForCapture(state);

                ContainerHelper.BlockDataSnapshot snapshot = ContainerHelper.captureAndClearBlockEntity(serverLevel, pos);
                CompoundTag tileTag = snapshot != null ? snapshot.nbt() : null;

                int restoreDelay = ModConfig.RESTORE_DELAY_TICKS.get();

                GhostBlockEntity ghost = new GhostBlockEntity(serverLevel, pos, cleanState, tileTag, restoreDelay, GhostType.RESTORE);
                serverLevel.addFreshEntity(ghost);

                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                cir.setReturnValue(true);
            }
        }
    }
}