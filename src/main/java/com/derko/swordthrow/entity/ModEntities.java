package com.derko.swordthrow.entity;

import com.derko.swordthrow.SwordThrowMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final RegistryKey<EntityType<?>> THROWN_SWORD_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, SwordThrowMod.id("thrown_sword"));

    public static final EntityType<ThrownSwordEntity> THROWN_SWORD = Registry.register(
        Registries.ENTITY_TYPE,
        THROWN_SWORD_KEY,
        EntityType.Builder.<ThrownSwordEntity>create(ThrownSwordEntity::new, SpawnGroup.MISC)
            .dimensions(0.5F, 0.5F)
            .maxTrackingRange(6)
            .trackingTickInterval(2)
            .build(THROWN_SWORD_KEY)
    );

    private ModEntities() {
    }

    public static void register() {
        SwordThrowMod.LOGGER.info("Registered entities for {}", SwordThrowMod.MOD_ID);
    }
}
