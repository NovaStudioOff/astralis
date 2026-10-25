package com.astralis.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Bouteille d'oxygène : clic droit pour recharger le réservoir du plastron spatial. */
public class OxygenCanisterItem extends Item {
    /** 2000 ticks = ~1 min 40 d'oxygène par bouteille. */
    public static final int REFILL = 2000;

    public OxygenCanisterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);

        if (!(chest.getItem() instanceof SpaceSuitItem)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("msg.astralis.canister_needs_suit"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (SpaceSuitItem.getOxygen(chest) >= SpaceSuitItem.MAX_OXYGEN) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("msg.astralis.tank_full"), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            SpaceSuitItem.setOxygen(chest, SpaceSuitItem.getOxygen(chest) + REFILL);
            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
