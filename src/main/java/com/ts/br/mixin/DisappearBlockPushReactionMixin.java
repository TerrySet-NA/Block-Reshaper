package com.ts.br.mixin;

import com.ts.br.util.GhostBlockTracker;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class DisappearBlockPushReactionMixin {

    /**
     * 當任何機械、活塞、結構搬運工具查詢推動反應時：
     * 若剛才查詢的座標是怪物放的暫態方塊，強制返回 PushReaction.BLOCK！
     */
    @Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
    private void br$forceDisappearBlockAsUnmovable(CallbackInfoReturnable<PushReaction> cir) {
        if (GhostBlockTracker.isLastQueriedPosDisappear()) {
            // 直接宣告為不可推動（等同黑曜石/基岩）
            cir.setReturnValue(PushReaction.BLOCK);
        }
    }
}