package com.astralis.item;

import com.astralis.registry.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.LazyLoadedValue;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

public enum ModArmorMaterials implements ArmorMaterial {
    // Nom "astralis:space_suit" -> textures/models/armor/space_suit_layer_1.png / _layer_2.png
    SPACE_SUIT("astralis:space_suit", 25, new int[]{2, 5, 6, 2}, 12,
            SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.0F,
            () -> Ingredient.of(ModItems.TITANIUM_PLATE.get()));

    private static final int[] BASE_DURABILITY = {13, 15, 16, 11}; // pieds, jambes, torse, tête

    private final String name;
    private final int durabilityMultiplier;
    private final int[] protection;
    private final int enchantability;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final LazyLoadedValue<Ingredient> repair;

    ModArmorMaterials(String name, int durabilityMultiplier, int[] protection, int enchantability,
                      SoundEvent equipSound, float toughness, float knockbackResistance,
                      Supplier<Ingredient> repair) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.protection = protection;
        this.enchantability = enchantability;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repair = new LazyLoadedValue<>(repair);
    }

    @Override public int getDurabilityForSlot(EquipmentSlot slot) { return BASE_DURABILITY[slot.getIndex()] * durabilityMultiplier; }
    @Override public int getDefenseForSlot(EquipmentSlot slot) { return protection[slot.getIndex()]; }
    @Override public int getEnchantmentValue() { return enchantability; }
    @Override public SoundEvent getEquipSound() { return equipSound; }
    @Override public Ingredient getRepairIngredient() { return repair.get(); }
    @Override public String getName() { return name; }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockbackResistance; }
}
