package com.ts.br.mixin;

import com.ts.br.block.GhostType;
import com.ts.br.config.ModConfig;
import com.ts.br.entity.GhostBlockEntity;
import net.minecraft.core.BlockPos;
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

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class EndermanLeaveBlockMixin {

    @Shadow @Final private EnderMan enderman;

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean br$interceptEndermanPlaceBlock(Level level, BlockPos pos, BlockState state, int flags) {
        boolean result = level.setBlock(pos, state, flags);

        if (result && ModConfig.ENABLED.get() && level instanceof ServerLevel serverLevel) {
            if (serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                int restoreDelay = ModConfig.RESTORE_DELAY_TICKS.get();
                GhostBlockEntity ghost = new GhostBlockEntity(serverLevel, pos, state, null, restoreDelay, GhostType.DISAPPEAR);
                serverLevel.addFreshEntity(ghost);
            }
        }

        return result;
    }
}