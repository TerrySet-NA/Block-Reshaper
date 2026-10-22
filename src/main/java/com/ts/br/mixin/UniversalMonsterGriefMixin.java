package com.ts.br.mixin;

import com.ts.br.block.GhostType;
import com.ts.br.config.ModConfig;
import com.ts.br.entity.GhostBlockEntity;
import com.ts.br.event.ModBlockEvents;
import com.ts.br.util.ContainerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
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

        boolean isMonster = entity instanceof Monster;
        boolean isMob = entity instanceof Mob && !(entity instanceof Player);
        boolean isDragon = entity instanceof EnderDragon;

        boolean allow = false;
        if (isDragon) {
            allow = ModConfig.INCLUDE_ENDER_DRAGON.get();
        } else if (isMonster) {
            allow = true;
        } else if (isMob && ModConfig.ALLOW_NON_MONSTERS.get()) {
            allow = true;
        }

        if (allow) {
            BlockState state = serverLevel.getBlockState(pos);

            if (!state.isAir() && !ModBlockEvents.isNonRestorable(state)) {
                ContainerHelper.BlockDataSnapshot snapshot = ContainerHelper.captureAndClearBlockEntity(serverLevel, pos);
                CompoundTag tileTag = snapshot != null ? snapshot.nbt() : null;

                int restoreDelay = ModConfig.RESTORE_DELAY_TICKS.get();

                GhostBlockEntity ghost = new GhostBlockEntity(serverLevel, pos, state, tileTag, restoreDelay, GhostType.RESTORE);
                serverLevel.addFreshEntity(ghost);

                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                cir.setReturnValue(true);
            }
        }
    }
}