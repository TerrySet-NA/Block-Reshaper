package com.ts.br.mixin;

import com.ts.br.util.GhostBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelGetBlockStateMixin {

    @Inject(method = "getBlockState", at = @At("HEAD"))
    private void br$capturePos(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (pos != null) {
            GhostBlockTracker.setLastQueriedPos(pos);
        }
    }
}