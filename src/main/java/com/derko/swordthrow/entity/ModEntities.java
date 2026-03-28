package com.derko.swordthrow.entity;

import com.derko.swordthrow.SwordThrowMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, SwordThrowMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownSwordEntity>> THROWN_SWORD =
        ENTITY_TYPES.register("thrown_sword", () ->
            EntityType.Builder.<ThrownSwordEntity>of(ThrownSwordEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(6)
                .updateInterval(2)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, SwordThrowMod.id("thrown_sword")))
        );

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
