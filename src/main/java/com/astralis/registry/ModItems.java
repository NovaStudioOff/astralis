package com.astralis.registry;

import com.astralis.AstralisMod;
import com.astralis.item.ModArmorMaterials;
import com.astralis.item.OxygenCanisterItem;
import com.astralis.item.RocketItem;
import com.astralis.item.SpaceSuitItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AstralisMod.MODID);

    private static Item.Properties props() {
        return new Item.Properties().tab(AstralisMod.TAB);
    }

    // Matériaux
    public static final RegistryObject<Item> RAW_TITANIUM = ITEMS.register("raw_titanium", () -> new Item(props()));
    public static final RegistryObject<Item> TITANIUM_INGOT = ITEMS.register("titanium_ingot", () -> new Item(props()));
    public static final RegistryObject<Item> TITANIUM_PLATE = ITEMS.register("titanium_plate", () -> new Item(props()));
    public static final RegistryObject<Item> ELECTRONIC_CIRCUIT = ITEMS.register("electronic_circuit", () -> new Item(props()));

    // Survie spatiale
    public static final RegistryObject<Item> OXYGEN_CANISTER = ITEMS.register("oxygen_canister",
            () -> new OxygenCanisterItem(props().stacksTo(16)));
    public static final RegistryObject<Item> ROCKET_FUEL = ITEMS.register("rocket_fuel",
            () -> new Item(props().stacksTo(16)));

    // Fusée
    public static final RegistryObject<Item> ROCKET = ITEMS.register("rocket",
            () -> new RocketItem(props().stacksTo(1)));

    // Combinaison spatiale
    public static final RegistryObject<Item> SPACE_HELMET = ITEMS.register("space_helmet",
            () -> new SpaceSuitItem(ModArmorMaterials.SPACE_SUIT, EquipmentSlot.HEAD, props()));
    public static final RegistryObject<Item> SPACE_CHESTPLATE = ITEMS.register("space_chestplate",
            () -> new SpaceSuitItem(ModArmorMaterials.SPACE_SUIT, EquipmentSlot.CHEST, props()));
    public static final RegistryObject<Item> SPACE_LEGGINGS = ITEMS.register("space_leggings",
            () -> new SpaceSuitItem(ModArmorMaterials.SPACE_SUIT, EquipmentSlot.LEGS, props()));
    public static final RegistryObject<Item> SPACE_BOOTS = ITEMS.register("space_boots",
            () -> new SpaceSuitItem(ModArmorMaterials.SPACE_SUIT, EquipmentSlot.FEET, props()));
}
