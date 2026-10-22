package com.ts.br.event;

import com.ts.br.BlockRestoreMod;
import com.ts.br.block.GhostType;
import com.ts.br.config.ModConfig;
import com.ts.br.entity.GhostBlockEntity;
import com.ts.br.util.ContainerHelper;
import com.ts.br.util.GhostBlockTracker;
import com.ts.br.util.RestoreUpdateQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = BlockRestoreMod.MODID)
public class ModBlockEvents {

    public static boolean isNonRestorable(BlockState state) {
        // 1. 空氣不還原
        if (state.isAir()) return true;

        // 2. 純流體方塊不還原（但含水固體方塊如含水樓梯會正常還原）
        if (state.getBlock() instanceof LiquidBlock) return true;

        // 3. 自身是可被覆蓋的脆弱方塊（草、花、雪層、火等）直接排除
        if (state.canBeReplaced()) return true;

        // 4. 多格方塊結構抽象過濾（檢查通用半身/床部屬性，不硬編碼 Class）
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) ||
            state.hasProperty(BlockStateProperties.BED_PART)) {
            return true;
        }

        // 5. Config 自訂不可還原黑名單（支援 ID 與 #Tag）
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

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // 當前 Tick 所有方塊都放置完畢後，統一執行形狀與鄰近更新
            RestoreUpdateQueue.flush(serverLevel);
        }
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

        boolean isMonster = source instanceof Monster;
        boolean isMob = source instanceof Mob && !(source instanceof Player);
        boolean isDragon = source instanceof EnderDragon;
        boolean isCrystal = direct instanceof EndCrystal || indirect instanceof EndCrystal;

        boolean allowRestore = false;
        if (isDragon) {
            allowRestore = ModConfig.INCLUDE_ENDER_DRAGON.get();
        } else if (isCrystal) {
            allowRestore = ModConfig.INCLUDE_END_CRYSTAL.get();
        } else if (isMonster) {
            allowRestore = true;
        } else if (isMob && ModConfig.ALLOW_NON_MONSTERS.get()) {
            allowRestore = true;
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

            ContainerHelper.BlockDataSnapshot snapshot = ContainerHelper.captureAndClearBlockEntity(level, pos);
            CompoundTag tileTag = snapshot != null ? snapshot.nbt() : null;

            toRestoreList.add(new PendingRestore(pos.immutable(), state, tileTag));
            it.remove();
        }

        for (PendingRestore entry : toRestoreList) {
            GhostBlockEntity ghost = new GhostBlockEntity(
                    level, entry.pos(), entry.state(), entry.tag(), restoreDelay, GhostType.RESTORE
            );
            level.addFreshEntity(ghost);

            // Flag 18 靜默轉為空氣
            level.setBlock(entry.pos(), Blocks.AIR.defaultBlockState(), 18);

            AABB clearBox = new AABB(entry.pos()).inflate(0.5);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, clearBox);
            for (ItemEntity item : items) {
                item.discard();
            }
        }
    }
}