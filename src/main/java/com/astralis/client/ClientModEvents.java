package com.astralis.client;

import com.astralis.AstralisMod;
import com.astralis.client.sky.SkyRenderer;
import com.astralis.registry.ModEntities;
import com.astralis.space.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientModEvents {
    private ClientModEvents() {}

    /** Événements du bus MOD : HUD, modèle et rendu de la fusée. */
    @Mod.EventBusSubscriber(modid = AstralisMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.AIR_LEVEL.id(), "oxygen", OxygenOverlay.HUD);
        }

        @SubscribeEvent
        public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(RocketRenderer.LAYER, RocketModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.ROCKET.get(), RocketRenderer::new);
        }
    }

    /** Événements du bus FORGE : ciel spatial et silence du vide. */
    @Mod.EventBusSubscriber(modid = AstralisMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        /** Remplace le ciel par l'espace réel (Lune, orbite, haute altitude en fusée). */
        @SubscribeEvent
        public static void onRenderStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
            SkyRenderer.render(event.getPoseStack(), event.getPartialTick());
        }

        /**
         * Le son ne se propage pas dans le vide : seuls les bruits proches
         * (transmis par le sol, l'outil ou la structure) restent audibles.
         */
        @SubscribeEvent
        public static void onPlaySound(PlaySoundEvent event) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (mc.level == null || player == null || !ModDimensions.isVacuum(mc.level)) {
                return;
            }
            SoundInstance sound = event.getSound();
            if (sound == null || sound.isRelative()) return;

            SoundSource source = sound.getSource();
            if (source == SoundSource.MASTER || source == SoundSource.MUSIC
                    || source == SoundSource.RECORDS || source == SoundSource.VOICE) {
                return;
            }
            if (source == SoundSource.WEATHER) {
                event.setSound(null);
                return;
            }
            double distSqr = player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ());
            if (distSqr > 36.0) {
                event.setSound(null);
            }
        }
    }
}
