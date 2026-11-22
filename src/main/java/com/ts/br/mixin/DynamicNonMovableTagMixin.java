package com.ts.br.mixin;

import com.ts.br.util.GhostBlockTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class DynamicNonMovableTagMixin {

    /**
     * 當任何機械/物理系統（Create、Aeronautics 等）詢問不可移動標籤時：
     * 若為暫態方塊，直接回傳 true，讓系統將其視為天然基岩/固定地基略過，不破壞原本的生命週期。
     */
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void br$interceptNonMovableTags(TagKey<Block> tag, CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation loc = tag.location();
        String path = loc.getPath();

        if (path.contains("non_movable") || path.contains("unmovable")) {
            if (GhostBlockTracker.isLastQueriedPosDisappear()) {
                cir.setReturnValue(true);
            }
        }
    }
}