package com.ts.br.event;

import com.mojang.logging.LogUtils;
import com.ts.br.BlockRestoreMod;
import com.ts.br.block.GhostType;
import com.ts.br.config.ModConfig;
import com.ts.br.entity.GhostBlockEntity;
import com.ts.br.util.ContainerHelper;
import com.ts.br.util.EntityRestoreFilter;
import com.ts.br.util.GhostBlockTracker;
import com.ts.br.util.PendingPlacementTracker;
import com.ts.br.util.RestoreUpdateQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = BlockRestoreMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModBlockEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isNonRestorable(BlockState state) {
        if (state.isAir()) return true;
        if (state.getBlock() instanceof LiquidBlock) return true;
        if (state.canBeReplaced()) return true;

        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) ||
            state.hasProperty(BlockStateProperties.BED_PART)) {
            return true;
        }

        List<? extends String> blackList = ModConfig.NON_RESTORABLE_BLOCKS.get();
        if (!blackList.isEmpty()) {
            ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String blockId = blockKey.toString();

            for (String entry : blackList) {
                if (entry.startsWith("#")) {
                    ResourceLocation tagLoc = ResourceLocation.tryParse(entry.substring(1));
                    if (tagLoc != null && state.is(TagKey.create(Registries.BLOCK, tagLoc))) {
                        return true;
                    }
                } else if (entry.equals(blockId)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static BlockState sanitizeStateForCapture(BlockState state) {
        if (state.hasProperty(BlockStateProperties.SNOWY)) {
            return state.setValue(BlockStateProperties.SNOWY, false);
        }
        return state;
    }

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            RestoreUpdateQueue.flush(serverLevel);
            // 每一 Tick 結算所有剛放下的方塊
            PendingPlacementTracker.tick(serverLevel);
        }
    }

    /**
     * 全生物通用放置監聽：
     * 不排除安德、不排除任何模組怪。
     * 直接將事件登記進延遲驗證佇列，等待世界 setBlock 落地後以實體狀態為準。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !ModConfig.ENABLED.get()) return;
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;

        Entity placer = event.getEntity();
        if (!EntityRestoreFilter.shouldRestoreFor(placer)) return;

        BlockPos eventPos = event.getPos();
        BlockState placedState = event.getPlacedBlock();

        LOGGER.info("[BR] 捕捉到生物放置意圖: 實體={}, 報告座標={}, 報告方塊={}", 
                placer.getType().getDescription().getString(), eventPos, placedState);

        // 將事件登記進追蹤佇列，交由 Tick 結算真實落子格
        PendingPlacementTracker.enqueue(serverLevel, eventPos, placedState);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerBreak(BlockEvent.BreakEvent event) {
        BlockPos pos = event.getPos();
        if (GhostBlockTracker.isDisappearBlock(pos)) {
            event.setCanceled(true);
            GhostBlockEntity ghost = GhostBlockTracker.getEntity(pos);
            if (ghost != null && !ghost.isRemoved()) {
                ghost.triggerEarlyDisappear();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        BlockPos pos = event.getPos();
        if (GhostBlockTracker.isDisappearBlock(pos)) {
            event.getDrops().clear();
            event.setCanceled(true);

            GhostBlockEntity ghost = GhostBlockTracker.getEntity(pos);
            if (ghost != null && !ghost.isRemoved()) {
                ghost.triggerEarlyDisappear();
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!ModConfig.ENABLED.get()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;

        Entity direct = event.getExplosion().getDirectSourceEntity();
        Entity indirect = event.getExplosion().getIndirectSourceEntity();
        Entity source = indirect != null ? indirect : direct;

        boolean allowRestore;
        if (source instanceof PrimedTnt tnt) {
            allowRestore = EntityRestoreFilter.shouldRestoreForTnt(tnt);
        } else {
            allowRestore = EntityRestoreFilter.shouldRestoreFor(source);
        }

        if (!allowRestore) return;

        int restoreDelay = ModConfig.RESTORE_DELAY_TICKS.get();

        record PendingRestore(BlockPos pos, BlockState state, CompoundTag tag) {}
        List<PendingRestore> toRestoreList = new ArrayList<>();

        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();

            if (GhostBlockTracker.isDisappearBlock(pos)) {
                GhostBlockEntity g = GhostBlockTracker.getEntity(pos);
                if (g != null && !g.isRemoved()) g.discard();
                GhostBlockTracker.unregister(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                it.remove();
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (isNonRestorable(state)) continue;

            BlockState cleanState = sanitizeStateForCapture(state);
            ContainerHelper.BlockDataSnapshot snapshot = ContainerHelper.captureAndClearBlockEntity(level, pos);
            CompoundTag tileTag = snapshot != null ? snapshot.nbt() : null;

            toRestoreList.add(new PendingRestore(pos.immutable(), cleanState, tileTag));
            it.remove();
        }

        for (PendingRestore entry : toRestoreList) {
            GhostBlockEntity ghost = new GhostBlockEntity(
                    level, entry.pos(), entry.state(), entry.tag(), restoreDelay, GhostType.RESTORE
            );
            level.addFreshEntity(ghost);
            level.setBlock(entry.pos(), Blocks.AIR.defaultBlockState(), 18);

            AABB clearBox = new AABB(entry.pos()).inflate(0.5);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, clearBox);
            for (ItemEntity item : items) {
                item.discard();
            }
        }
    }
}