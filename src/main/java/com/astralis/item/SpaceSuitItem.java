package com.astralis.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pièce de combinaison spatiale. Le plastron embarque le réservoir d'oxygène (NBT "Oxygen", en ticks).
 */
public class SpaceSuitItem extends ArmorItem {
    /** 6000 ticks = 5 minutes d'autonomie. */
    public static final int MAX_OXYGEN = 6000;
    private static final String OXYGEN_TAG = "Oxygen";

    public SpaceSuitItem(ArmorMaterial material, EquipmentSlot slot, Properties properties) {
        super(material, slot, properties);
    }

    public static boolean isFullSuit(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SpaceSuitItem
                && entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SpaceSuitItem
                && entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof SpaceSuitItem
                && entity.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof SpaceSuitItem;
    }

    public static int getOxygen(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getInt(OXYGEN_TAG), 0, MAX_OXYGEN);
    }

    public static void setOxygen(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(OXYGEN_TAG, Mth.clamp(value, 0, MAX_OXYGEN));
    }

    private boolean isTank() {
        return getSlot() == EquipmentSlot.CHEST;
    }

    // La barre du plastron affiche l'oxygène restant (au lieu de la durabilité).
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return isTank() || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return isTank() ? Math.round(13.0F * getOxygen(stack) / MAX_OXYGEN) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return isTank() ? 0x4FC3F7 : super.getBarColor(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isTank()) {
            int percent = Math.round(100.0F * getOxygen(stack) / MAX_OXYGEN);
            MutableComponent line = Component.translatable("tooltip.astralis.oxygen", percent);
            tooltip.add(line.withStyle(ChatFormatting.AQUA));
        }
    }
}
