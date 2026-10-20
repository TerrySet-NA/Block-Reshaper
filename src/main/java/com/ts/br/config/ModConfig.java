package com.ts.br.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {

    // ==================== COMMON (伺服端 / 邏輯規則) ====================
    public static final ModConfigSpec COMMON_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue ALLOW_NON_MONSTERS;
    public static final ModConfigSpec.BooleanValue INCLUDE_END_CRYSTAL;
    public static final ModConfigSpec.BooleanValue INCLUDE_ENDER_DRAGON;

    public static final ModConfigSpec.IntValue RESTORE_DELAY_TICKS;
    public static final ModConfigSpec.IntValue BUFFER_DELAY_TICKS;
    public static final ModConfigSpec.IntValue END_HOLD_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("general");

        ENABLED = builder
                .comment("Master switch: Whether the entire block restore mod is enabled.")
                .define("enabled", true);

        ALLOW_NON_MONSTERS = builder
                .comment("Allow Non-Monsters: If true, all living mobs (animals, golems, neutral mobs) can trigger restore. If false, strictly monsters.")
                .define("allowNonMonsters", true);

        INCLUDE_END_CRYSTAL = builder
                .comment("Include End Crystal: Whether to restore destruction caused by End Crystal explosions.")
                .define("includeEndCrystal", true);

        INCLUDE_ENDER_DRAGON = builder
                .comment("Include Ender Dragon: Whether to restore destruction caused by the Ender Dragon.")
                .define("includeEnderDragon", true);

        RESTORE_DELAY_TICKS = builder
                .comment("Lifespan in ticks for waiting phase before main animation starts. Default: 300 ticks (15s).")
                .defineInRange("restoreDelayTicks", 300, 10, 72000);

        BUFFER_DELAY_TICKS = builder
                .comment("Hardware compensation delay in ticks. (Pre-place for RESTORE / Delayed-remove for DISAPPEAR). Default: 3 ticks.")
                .defineInRange("bufferDelayTicks", 3, 1, 30);

        END_HOLD_TICKS = builder
                .comment("Extra hold ticks after main animation completes before entity discard. Default: 3 ticks.")
                .defineInRange("endHoldTicks", 3, 0, 40);

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
                .comment("Client-only: Max visual render distance in blocks. Disables rendering beyond this distance to boost FPS. Default: 32.")
                .defineInRange("renderDistanceBlocks", 64, 4, 512);

        builder.pop();
        CLIENT_SPEC = builder.build();
    }
}