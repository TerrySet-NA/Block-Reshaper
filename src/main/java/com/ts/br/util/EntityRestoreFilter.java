package com.ts.br.util;

import com.ts.br.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EntityRestoreFilter {

    /**
     * 終極溯源：穿透箭矢、拋射物、點燃者，找到真正的幕後發動實體
     */
    public static Entity resolveRootCause(@Nullable Entity entity) {
        if (entity == null) return null;

        Entity current = entity;
        for (int i = 0; i < 5; i++) {
            if (current instanceof Projectile projectile && projectile.getOwner() != null) {
                current = projectile.getOwner();
            } else if (current instanceof TraceableEntity traceable && traceable.getOwner() != null) {
                current = traceable.getOwner();
            } else {
                break;
            }
        }
        return current;
    }

    public static boolean shouldRestoreFor(@Nullable Entity rawEntity) {
        if (rawEntity == null) {
            return false;
        }

        // 1. 取得真正源頭實體
        Entity rootEntity = resolveRootCause(rawEntity);
        if (rootEntity == null) {
            rootEntity = rawEntity;
        }

        String rootTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(rootEntity.getType()).toString();
        String rawTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(rawEntity.getType()).toString();

        // 2. 白名單最高優先級（無論是表面實體或源頭實體符合，直接放行）
        List<? extends String> whitelist = ModConfig.RESTORE_ENTITY_WHITELIST.get();
        if (whitelist.contains(rawTypeId) || whitelist.contains(rootTypeId)) {
            return true;
        }

        // 3. 真實玩家操作排除
        if (rootEntity instanceof ServerPlayer player && !(player instanceof FakePlayer) && player.connection != null) {
            return false;
        }

        // 4. 黑名單次級排除
        List<? extends String> blacklist = ModConfig.RESTORE_ENTITY_BLACKLIST.get();
        if (blacklist.contains(rawTypeId) || blacklist.contains(rootTypeId)) {
            return false;
        }

        // 5. 終界龍獨立規則
        if (rootEntity instanceof EnderDragon) {
            return ModConfig.INCLUDE_ENDER_DRAGON.get();
        }

        // 6. 若溯源後依然不是生物（例如無人引爆的純物理實體），因前述未命中白名單，直接拒絕
        if (!(rootEntity instanceof LivingEntity)) {
            return false;
        }

        // 7. 預設非玩家生物判定
        if (ModConfig.ALLOW_NON_MONSTERS.get()) {
            return true;
        } else {
            return rootEntity instanceof Monster;
        }
    }

    public static boolean shouldRestoreForTnt(PrimedTnt tnt) {
        LivingEntity igniter = tnt.getOwner();
        if (igniter != null) {
            return shouldRestoreFor(igniter);
        }
        return ModConfig.RESTORE_UNOWNED_TNT.get();
    }
}