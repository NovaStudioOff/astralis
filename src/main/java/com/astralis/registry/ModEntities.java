package com.astralis.registry;

import com.astralis.AstralisMod;
import com.astralis.entity.RocketEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AstralisMod.MODID);

    public static final RegistryObject<EntityType<RocketEntity>> ROCKET = ENTITIES.register("rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                    .sized(2.0F, 9.5F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build(new ResourceLocation(AstralisMod.MODID, "rocket").toString()));
}
