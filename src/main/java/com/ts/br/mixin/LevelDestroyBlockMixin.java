package com.ts.br.mixin;

import com.ts.br.entity.GhostBlockEntity;
import com.ts.br.util.GhostBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelDestroyBlockMixin {

    /**
     * 任何模組只要把這格方塊打成空氣，自動同步清理幽靈實體
     */
    @Inject(
        method = "destroyBlock",
        at = @At("HEAD")
    )
    private void br$onDestroyBlock(BlockPos pos, boolean drop, Entity entity, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (GhostBlockTracker.isDisappearBlock(pos)) {
            GhostBlockEntity ghost = GhostBlockTracker.getEntity(pos);
            if (ghost != null && !ghost.isRemoved()) {
                // 標記並快速移除幽靈實體，避免重複觸發
                ghost.discard();
            }
            GhostBlockTracker.unregister(pos);
        }
    }
}