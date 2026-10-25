package com.astralis.space;

import com.astralis.AstralisMod;
import com.astralis.damage.VacuumDamageSource;
import com.astralis.entity.RocketEntity;
import com.astralis.item.SpaceSuitItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Règles physiques de l'espace :
 *  - Lune : gravité 1/6 g ; orbite : apesanteur ;
 *  - vide : sans combinaison complète + oxygène, hypoxie puis dégâts de décompression ;
 *  - pas d'oxygène : impossible d'allumer un feu, et le feu s'éteint ;
 *  - l'eau liquide s'évapore instantanément dans le vide ;
 *  - la cabine de la fusée est pressurisée.
 */
@Mod.EventBusSubscriber(modid = AstralisMod.MODID)
public class SpaceEvents {
    private static final double GRAVITY = 0.08;                    // gravité vanilla (blocs/tick²)
    private static final double LUNAR_RATIO = 1.0 / 6.0;

    private static final String EXPOSURE_TAG = "astralis_exposure";
    private static final int LOW_OXYGEN = 1200;                    // 1 minute

    private static boolean onMoon(Entity entity) {
        return entity.level.dimension().equals(ModDimensions.MOON);
    }

    private static boolean inOrbit(Entity entity) {
        return entity.level.dimension().equals(ModDimensions.ORBIT);
    }

    // ---------------- Gravité ----------------

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        Entity entity = event.getEntity();
        if (!ModDimensions.isVacuum(entity.level) || !(entity instanceof LivingEntity living)) return;

        // Pas d'oxygène -> pas de combustion
        if (!living.level.isClientSide && living.isOnFire()) {
            living.clearFire();
        }

        if (living.isNoGravity() || living.isOnGround() || living.isInWaterOrBubble() || living.isInLava()
                || living.onClimbable() || living.isFallFlying() || living.isPassenger()
                || living.hasEffect(MobEffects.LEVITATION)) {
            return;
        }
        if (living instanceof Player player && player.getAbilities().flying) return;

        Vec3 motion = living.getDeltaMovement();
        double ratio = inOrbit(living) ? 0.0 : LUNAR_RATIO;
        double extra = GRAVITY * (1.0 - ratio);

        double ny = motion.y + extra;
        double nx = motion.x;
        double nz = motion.z;

        // Sortie extravéhiculaire : en apesanteur, Z / S propulsent le long de la direction du regard
        if (inOrbit(living) && living.level.isClientSide && living instanceof Player p) {
            float forward = p.zza;
            if (forward != 0.0F) {
                Vec3 look = p.getLookAngle().scale(0.045 * forward);
                nx += look.x;
                ny += look.y;
                nz += look.z;
            }
        }
        living.setDeltaMovement(nx, ny, nz);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        Entity entity = event.getEntity();
        if (!onMoon(entity)) return;
        // Combinaison encombrante : saut ~2 blocs
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, motion.y * 0.65, motion.z);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        Entity entity = event.getEntity();
        if (inOrbit(entity)) {
            event.setDistance(0.0F);
        } else if (onMoon(entity)) {
            // Énergie d'impact proportionnelle à g*h : 6 fois moins de dégâts.
            event.setDistance((float) (event.getDistance() * LUNAR_RATIO));
        }
    }

    // ---------------- Oxygène et vide ----------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level.isClientSide) return;

        CompoundTag data = player.getPersistentData();
        // Cabine de fusée pressurisée : pas d'exposition, pas de consommation d'oxygène
        if (!ModDimensions.isVacuum(player.level) || player.getVehicle() instanceof RocketEntity) {
            if (data.getInt(EXPOSURE_TAG) != 0) data.putInt(EXPOSURE_TAG, 0);
            return;
        }
        if (player.isCreative() || player.isSpectator()) return;

        boolean fullSuit = SpaceSuitItem.isFullSuit(player);
        boolean breathing = false;

        if (fullSuit) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            int oxygen = SpaceSuitItem.getOxygen(chest);
            if (oxygen > 0) {
                breathing = true;
                if (player.tickCount % 10 == 0) {
                    oxygen = Math.max(0, oxygen - 10);
                    SpaceSuitItem.setOxygen(chest, oxygen);
                }
                if (oxygen > 0 && oxygen <= LOW_OXYGEN && player.tickCount % 100 == 0) {
                    player.displayClientMessage(Component.translatable("msg.astralis.low_oxygen"), true);
                }
            }
        }

        int exposure = data.getInt(EXPOSURE_TAG);
        if (breathing) {
            if (exposure != 0) data.putInt(EXPOSURE_TAG, 0);
            return;
        }

        exposure++;
        data.putInt(EXPOSURE_TAG, exposure);

        if (exposure % 20 == 1) {
            player.displayClientMessage(Component.translatable(
                    fullSuit ? "msg.astralis.no_oxygen" : "msg.astralis.vacuum"), true);
        }
        // ~2 s : vertiges ; ~5 s : cécité ; ensuite décompression.
        if (exposure > 40 && exposure % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, false));
        }
        if (exposure > 100 && exposure % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
        }
        if (exposure > 100 && exposure % 10 == 0) {
            player.hurt(VacuumDamageSource.INSTANCE, 2.0F);
        }
    }

    // ---------------- Pas d'air : pas de feu, l'eau s'évapore ----------------

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handleUse(event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        handleUse(event);
    }

    private static void handleUse(PlayerInteractEvent event) {
        Entity entity = event.getEntity();
        if (!ModDimensions.isVacuum(entity.level) || !(entity instanceof Player player)) return;

        ItemStack stack = event.getItemStack();
        String messageKey = null;
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
            messageKey = "msg.astralis.no_fire";
        } else if (stack.is(Items.WATER_BUCKET)) {
            messageKey = "msg.astralis.no_water";
        }
        if (messageKey == null) return;

        event.setCanceled(true);
        if (!player.level.isClientSide) {
            player.displayClientMessage(Component.translatable(messageKey), true);
            player.level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
    }

    // ---------------- Sécurité du vol : pas de sortie en pleine manœuvre ----------------

    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (event.isMounting()) return;
        Entity vehicle = event.getEntityBeingMounted();
        Entity rider = event.getEntityMounting();
        if (!(vehicle instanceof RocketEntity rocket)) return;
        if (rocket.transferring || rocket.isRemoved() || !rider.isAlive()) return;

        int state = rocket.getState();
        boolean inFlight = state == RocketEntity.COUNTDOWN || state == RocketEntity.ASCENT
                || state == RocketEntity.TRANSIT || state == RocketEntity.DESCENT;
        if (inFlight) {
            event.setCanceled(true);
            if (!rider.level.isClientSide && rider instanceof Player player) {
                player.displayClientMessage(Component.translatable("msg.astralis.rocket_busy"), true);
            }
        }
    }
}
