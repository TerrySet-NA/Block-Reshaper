package com.ts.br.mixin;

import com.ts.br.util.GhostBlockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {

    /**
     * 當原版結構方塊、Create 組裝裝置、空間搬移工具在遍歷世界打包方塊時進行重定向：
     * 若發現目標格是即將消失的暫態方塊，對採樣器謊報該格是「空氣」！
     */
    @Redirect(
        method = "fillFromWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState br$hideDisappearBlockFromStructure(Level level, BlockPos pos) {
        BlockState actualState = level.getBlockState(pos);

        // 如果外部工具正試圖將這顆方塊打包進結構 / Contraption：
        if (GhostBlockTracker.isDisappearBlock(pos)) {
            // 回傳空氣，讓外部結構只打包空氣進去，徹底杜絕複製！
            return Blocks.AIR.defaultBlockState();
        }

        return actualState;
    }
}