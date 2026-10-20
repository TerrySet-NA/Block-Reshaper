package com.ts.br.registry;

import com.ts.br.BlockRestoreMod;
import com.ts.br.entity.GhostBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BlockRestoreMod.MODID);

    public static final Supplier<EntityType<GhostBlockEntity>> GB =
            ENTITIES.register("gb", () ->
                    EntityType.Builder.<GhostBlockEntity>of(GhostBlockEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(24)
                            .updateInterval(1)
                            .fireImmune()
                            .build("gb")
            );
}