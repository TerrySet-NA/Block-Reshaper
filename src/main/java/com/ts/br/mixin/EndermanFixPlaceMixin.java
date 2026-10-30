package com.ts.br.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class EndermanFixPlaceMixin {

    @Shadow @Final private EnderMan enderman;

    /**
     * 攔截 EventHooks.onBlockPlace(Entity, BlockSnapshot, Direction)
     * 將原本錯誤指向地面 (blockpos1) 的 BlockSnapshot，
     * 改為直接指向正確的落子空間 (blockpos) 與安德手中持有的方塊！
     */
    @ModifyArg(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/event/EventHooks;onBlockPlace(Lnet/minecraft/world/entity/Entity;Lnet/neoforged/neoforge/common/util/BlockSnapshot;Lnet/minecraft/core/Direction;)Z"
        ),
        index = 1
    )
    private BlockSnapshot br$fixEndermanBlockSnapshot(BlockSnapshot originalSnapshot) {
        Level level = this.enderman.level();
        // originalSnapshot 的座標是 blockpos1 (地面)，真正的目標格是其上方一格 (blockpos)
        BlockPos actualPlacedPos = originalSnapshot.getPos().above();
        BlockState carriedState = this.enderman.getCarriedBlock();

        if (carriedState == null) {
            carriedState = level.getBlockState(actualPlacedPos);
        }

        return BlockSnapshot.create(level.dimension(), level, actualPlacedPos);
    }
}