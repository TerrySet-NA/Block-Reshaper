package com.ts.br.mixin;

import com.ts.br.config.ModConfig;
import com.ts.br.event.ModBlockEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderMan.class)
public abstract class EndermanDeathMixin {

    @Shadow
    public abstract BlockState getCarriedBlock();

    @Shadow
    public abstract void setCarriedBlock(BlockState state);

    @Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
    private void br$preventCarriedBlockDropOnDeath(ServerLevel level, DamageSource damageSource, boolean recentlyHit, CallbackInfo ci) {
        if (ModConfig.ENABLED.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            BlockState carried = this.getCarriedBlock();
            if (carried != null) {
                // 若屬於不可還原黑名單（直接破壞/原生保留），放行原版掉落物邏輯
                if (ModBlockEvents.isNonRestorable(carried)) {
                    return;
                }
                // 一般方塊清空，避免玩家藉由擊殺安德刷取方塊
                this.setCarriedBlock(null);
            }
        }
    }
}