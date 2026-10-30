package com.ts.br.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jetbrains.annotations.Nullable;

public class ContainerHelper {

    public record BlockDataSnapshot(@Nullable CompoundTag nbt, DataComponentMap components) {}

    @Nullable
    public static BlockDataSnapshot captureAndClearBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        // 1. 抓取 1.21+ 強型別組件
        DataComponentMap components = be.components();

        // 2. 抓取完整後備 NBT
        CompoundTag nbt = be.saveWithFullMetadata(level.registryAccess());

        // 3. 核心修復：單體隔離清空
        // 若為箱子，嚴格只操作該 BlockEntity 本身的 27 格，絕不呼叫外層 Capability (防止 CombinedInvWrapper 連帶清空隔壁)
        if (be instanceof ChestBlockEntity chestBe) {
            for (int i = 0; i < chestBe.getContainerSize(); i++) {
                chestBe.setItem(i, ItemStack.EMPTY);
            }
            chestBe.setChanged();
        } else if (be instanceof Container container) {
            // 一般常規原版容器
            container.clearContent();
            be.setChanged();
        }

        return new BlockDataSnapshot(nbt, components);
    }
}