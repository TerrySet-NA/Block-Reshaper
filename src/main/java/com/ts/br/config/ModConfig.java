package com.ts.br.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class ModConfig {

    // ==================== COMMON (伺服端 / 邏輯規則) ====================
    public static final ModConfigSpec COMMON_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;

    // 生物範圍與特殊 Boss 開關
    public static final ModConfigSpec.BooleanValue ALLOW_NON_MONSTERS;
    public static final ModConfigSpec.BooleanValue INCLUDE_ENDER_DRAGON;

    // 白名單優先高於黑名單
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESTORE_ENTITY_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESTORE_ENTITY_BLACKLIST;

    // TNT 鑑別
    public static final ModConfigSpec.BooleanValue RESTORE_UNOWNED_TNT;

    // 怪物放置行為參數
    public static final ModConfigSpec.IntValue MONSTER_BLOCK_DISAPPEAR_DELAY;

    // 時間參數
    public static final ModConfigSpec.IntValue RESTORE_DELAY_TICKS;
    public static final ModConfigSpec.IntValue BUFFER_DELAY_TICKS;
    public static final ModConfigSpec.IntValue END_HOLD_TICKS;

    // 不可還原 / 走原版直接破壞的方塊黑名單
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NON_RESTORABLE_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("general");

        ENABLED = builder
                .comment("Master switch: Whether the entire block restore mod is enabled.")
                .define("enabled", true);

        ALLOW_NON_MONSTERS = builder
                .comment("Allow Non-Monsters: If true, all living mobs (animals, golems, neutral mobs) can trigger restore. If false, strictly monsters.")
                .define("allowNonMonsters", true);

        INCLUDE_ENDER_DRAGON = builder
                .comment("Include Ender Dragon: Whether to restore destruction caused by the Ender Dragon.")
                .define("includeEnderDragon", true);

        RESTORE_ENTITY_WHITELIST = builder
                .comment("Whitelist: Entities that ALWAYS trigger restoration, overriding the blacklist. (e.g., 'minecraft:creeper', 'minecraft:end_crystal').")
                .defineListAllowEmpty("restoreWhitelist", List.of("minecraft:end_crystal"), o -> o instanceof String && ResourceLocation.tryParse((String) o) != null);

        RESTORE_ENTITY_BLACKLIST = builder
                .comment("Blacklist: Entities to EXCLUDE from restoration (e.g., 'minecraft:silverfish'). Ignored if entity is in whitelist.")
                .defineListAllowEmpty("restoreBlacklist", List.of(), o -> o instanceof String && ResourceLocation.tryParse((String) o) != null);

        RESTORE_UNOWNED_TNT = builder
                .comment("If true, TNT ignited by redstone/dispensers will trigger restoration. If false, treated as player automation (no restore).")
                .define("restoreUnownedTnt", false);

        MONSTER_BLOCK_DISAPPEAR_DELAY = builder
                .comment("Ticks before monster-placed blocks disappear. Default: 600 ticks (30s).")
                .defineInRange("monsterBlockDisappearDelay", 600, 20, 72000);

        RESTORE_DELAY_TICKS = builder
                .comment("Lifespan in ticks for waiting phase before main animation starts. Default: 300 ticks (15s).")
                .defineInRange("restoreDelayTicks", 300, 10, 72000);

        BUFFER_DELAY_TICKS = builder
                .comment("Hardware compensation delay in ticks. Default: 3 ticks.")
                .defineInRange("bufferDelayTicks", 3, 1, 30);

        END_HOLD_TICKS = builder
                .comment("Extra hold ticks after main animation completes before entity discard. Default: 3 ticks.")
                .defineInRange("endHoldTicks", 3, 0, 40);

        NON_RESTORABLE_BLOCKS = builder
                .comment("List of blocks that should NEVER be restored (directly handled by vanilla, won't disappear when placed, drops normally on enderman death). Supports block IDs ('minecraft:tnt') and tags ('#minecraft:beds').")
                .defineListAllowEmpty("nonRestorableBlocks", List.of(), o -> o instanceof String);

        builder.pop();
        COMMON_SPEC = builder.build();
    }

    // ==================== CLIENT (客戶端 / 視覺與效能) ====================
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.IntValue RENDER_DISTANCE_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("rendering");

        RENDER_DISTANCE_BLOCKS = builder
                .comment("Client-only: Max visual render distance in blocks. Default: 64.")
                .defineInRange("renderDistanceBlocks", 64, 4, 512);

        builder.pop();
        CLIENT_SPEC = builder.build();
    }
}