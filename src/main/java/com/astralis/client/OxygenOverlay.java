package com.astralis.client;

import com.astralis.item.SpaceSuitItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Jauge d'oxygène affichée au-dessus de la barre de faim quand le plastron spatial est porté. */
public final class OxygenOverlay {
    private OxygenOverlay() {}

    public static final IGuiOverlay HUD = (ForgeGui gui, PoseStack poseStack, float partialTick, int width, int height) -> {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof SpaceSuitItem)) return;

        int oxygen = SpaceSuitItem.getOxygen(chest);
        float ratio = (float) oxygen / SpaceSuitItem.MAX_OXYGEN;
        int percent = Math.round(ratio * 100.0F);

        int barWidth = 81;
        int barHeight = 5;
        int x = width / 2 + 10;
        int y = height - 60;

        boolean low = ratio < 0.2F;
        boolean blinkOff = low && (player.tickCount / 5) % 2 == 0;
        int fillColor = low ? 0xFFE53935 : 0xFF4FC3F7;

        GuiComponent.fill(poseStack, x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xAA000000);
        int filled = Math.round(barWidth * ratio);
        if (filled > 0 && !blinkOff) {
            GuiComponent.fill(poseStack, x, y, x + filled, y + barHeight, fillColor);
        }
        gui.getFont().draw(poseStack, Component.translatable("hud.astralis.oxygen", percent),
                x, y - 10, low ? 0xFFFF6659 : 0xFFFFFFFF);
    };
}
