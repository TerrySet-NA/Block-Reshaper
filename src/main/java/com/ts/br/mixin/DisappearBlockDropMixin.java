package com.ts.br.mixin;

import com.ts.br.util.GhostBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class DisappearBlockDropMixin {

    /**
     * 無論是原版破壞、玩家挖掘，還是 Create 機械鑽自行呼叫 Block.getDrops，
     * 只要這格座標在 GhostBlockTracker 內是 DISAPPEAR 方塊，回傳的掉落物一律為空！
     */
    @Inject(
        method = "getDrops",
        at = @At("HEAD"),
        cancellable = true
    )
    private void br$preventDisappearBlockDrops(LootParams.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        Vec3 origin = builder.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin != null) {
            BlockPos pos = BlockPos.containing(origin);
            if (GhostBlockTracker.isDisappearBlock(pos)) {
                // 強制返回空列表，Create 機械鑽即使自訂管道也什麼都拿不到！
                cir.setReturnValue(Collections.emptyList());
            }
        }
    }
}