package com.astralis;

import com.astralis.registry.ModBlocks;
import com.astralis.registry.ModEntities;
import com.astralis.registry.ModItems;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Astralis : conquête spatiale réaliste (Terre -> orbite -> Lune) pour Minecraft Forge 1.19.2.
 */
@Mod(AstralisMod.MODID)
public class AstralisMod {
    public static final String MODID = "astralis";

    public static final CreativeModeTab TAB = new CreativeModeTab(MODID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModItems.ROCKET.get());
        }
    };

    public AstralisMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
    }
}
