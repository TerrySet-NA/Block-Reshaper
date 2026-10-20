package com.ts.br.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

public class ContainerHelper {

    @Nullable
    public static CompoundTag captureAndClearBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());

        if (be instanceof ChestBlockEntity chestBe) {
            for (int i = 0; i < chestBe.getContainerSize(); i++) {
                chestBe.setItem(i, ItemStack.EMPTY);
            }
            chestBe.setChanged();
        } else if (be instanceof Container container) {
            container.clearContent();
        }

        clearItemHandler(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null));
        for (Direction dir : Direction.values()) {
            clearItemHandler(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir));
        }

        be.setChanged();
        return tag;
    }

    private static void clearItemHandler(@Nullable IItemHandler handler) {
        if (handler instanceof IItemHandlerModifiable modifiable) {
            for (int i = 0; i < modifiable.getSlots(); i++) {
                modifiable.setStackInSlot(i, ItemStack.EMPTY);
            }
        } else if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                while (!handler.extractItem(i, 64, false).isEmpty()) {}
            }
        }
    }
}