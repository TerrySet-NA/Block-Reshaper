package com.ts.br;

import com.ts.br.config.ModConfig;
import com.ts.br.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;

@Mod(BlockRestoreMod.MODID)
public class BlockRestoreMod {
    public static final String MODID = "br";

    public BlockRestoreMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.ENTITIES.register(modEventBus);

        // 分別註冊伺服端與客戶端設定檔
        modContainer.registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);
        modContainer.registerConfig(Type.CLIENT, ModConfig.CLIENT_SPEC);
    }
}