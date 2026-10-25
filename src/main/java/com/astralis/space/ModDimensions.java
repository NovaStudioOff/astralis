package com.astralis.space;

import com.astralis.AstralisMod;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModDimensions {
    public static final ResourceKey<Level> MOON = key("moon");
    public static final ResourceKey<Level> ORBIT = key("orbit");

    private ModDimensions() {}

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(AstralisMod.MODID, name));
    }

    /** Dimensions sans atmosphère : vide spatial. */
    public static boolean isVacuum(Level level) {
        ResourceKey<Level> k = level.dimension();
        return k.equals(MOON) || k.equals(ORBIT);
    }
}
