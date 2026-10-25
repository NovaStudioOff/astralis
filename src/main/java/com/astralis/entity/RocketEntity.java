package com.astralis.entity;

import com.astralis.item.SpaceSuitItem;
import com.astralis.registry.ModBlocks;
import com.astralis.registry.ModEntities;
import com.astralis.registry.ModItems;
import com.astralis.space.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Fusée pilotable : décollage depuis une plateforme, orbite, transfert Terre <-> Lune, atterrissage motorisé.
 *
 * Commandes du pilote : Z (avant) = lancer / transfert, S (arrière) = annuler / descendre.
 */
public class RocketEntity extends Entity {
    // États de vol
    public static final int IDLE = 0;      // posée sur une plateforme
    public static final int COUNTDOWN = 1; // compte à rebours
    public static final int ASCENT = 2;    // montée vers l'espace
    public static final int ORBIT = 3;     // en orbite
    public static final int TRANSIT = 4;   // transfert Terre <-> Lune
    public static final int DESCENT = 5;   // descente propulsée

    // Corps célestes
    public static final int EARTH = 0;
    public static final int MOON = 1;

    public static final float MAX_FUEL = 1000.0F;
    public static final float FUEL_PER_CANISTER = 100.0F;
    private static final float[] LAUNCH_COST = {150.0F, 60.0F};   // décollage Terre / Lune
    private static final float[] LANDING_COST = {0.0F, 100.0F};   // rentrée (aérofreinage) / alunissage
    private static final float TRANSFER_COST = 200.0F;
    private static final int COUNTDOWN_TICKS = 200;               // 10 s
    private static final int TRANSIT_TICKS = 1200;                // 60 s
    private static final int BURN_TICKS = 150;                    // 7,5 s de poussée au début du transfert
    private static final double[] CEILING = {300.0, 200.0};       // altitude de mise en orbite

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BODY =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> FUEL =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PROGRESS =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BURNING =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.BOOLEAN);

    private int countdown;
    private int transitTicks;
    private int padX;
    private int padZ;
    private double landingY;
    private int inputCooldown;
    private boolean staged;
    /** Vrai pendant un changement de dimension : autorise le désembarquement forcé. */
    public boolean transferring;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ------------------------------------------------------------------ données

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STATE, IDLE);
        this.entityData.define(BODY, EARTH);
        this.entityData.define(FUEL, 0.0F);
        this.entityData.define(PROGRESS, 0.0F);
        this.entityData.define(BURNING, false);
    }

    public int getState() { return this.entityData.get(STATE); }
    public int getBody() { return this.entityData.get(BODY); }
    public float getFuel() { return this.entityData.get(FUEL); }
    public float getProgress() { return this.entityData.get(PROGRESS); }
    public boolean isBurning() { return this.entityData.get(BURNING); }

    public void setBody(int body) { this.entityData.set(BODY, body); }
    public void setFuel(float fuel) { this.entityData.set(FUEL, Math.max(0.0F, Math.min(MAX_FUEL, fuel))); }
    private void setState(int state) { this.entityData.set(STATE, state); }
    private void setProgress(float p) { this.entityData.set(PROGRESS, p); }

    public void setPad(int x, int z) {
        this.padX = x;
        this.padZ = z;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setState(tag.getInt("State"));
        setBody(tag.getInt("Body"));
        setFuel(tag.getFloat("Fuel"));
        setProgress(tag.getFloat("Progress"));
        countdown = tag.getInt("Countdown");
        transitTicks = tag.getInt("TransitTicks");
        padX = tag.getInt("PadX");
        padZ = tag.getInt("PadZ");
        landingY = tag.getDouble("LandingY");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("State", getState());
        tag.putInt("Body", getBody());
        tag.putFloat("Fuel", getFuel());
        tag.putFloat("Progress", getProgress());
        tag.putInt("Countdown", countdown);
        tag.putInt("TransitTicks", transitTicks);
        tag.putInt("PadX", padX);
        tag.putInt("PadZ", padZ);
        tag.putDouble("LandingY", landingY);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ------------------------------------------------------------------ physique / interaction

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }
    @Override public double getPassengersRidingOffset() { return 6.0D; }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(getX() + 2.0D, getY(), getZ());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ROCKET.get());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // Ravitaillement
        if (held.is(ModItems.ROCKET_FUEL.get())) {
            if (!level.isClientSide) {
                if (getState() != IDLE) {
                    player.displayClientMessage(Component.translatable("msg.astralis.rocket_busy"), true);
                } else if (getFuel() + FUEL_PER_CANISTER > MAX_FUEL + 0.01F) {
                    player.displayClientMessage(Component.translatable("msg.astralis.rocket_full"), true);
                } else {
                    setFuel(getFuel() + FUEL_PER_CANISTER);
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUCKET_FILL_LAVA, SoundSource.NEUTRAL, 1.0F, 0.8F);
                    player.displayClientMessage(Component.translatable("msg.astralis.rocket_fueled",
                            Math.round(getFuel()), Math.round(MAX_FUEL)), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Embarquement
        if (!level.isClientSide) {
            if (!getPassengers().isEmpty()) {
                return InteractionResult.CONSUME;
            }
            if (!SpaceSuitItem.isFullSuit(player)) {
                player.displayClientMessage(Component.translatable("msg.astralis.rocket_need_suit"), true);
                return InteractionResult.CONSUME;
            }
            if (player.startRiding(this)) {
                player.displayClientMessage(Component.translatable("msg.astralis.rocket_boarded",
                        Math.round(getFuel()), Math.round(MAX_FUEL)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Démonter la fusée (au sol, vide) : elle redevient un objet qui conserve son carburant. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level.isClientSide || isRemoved()) return false;
        if (source.getEntity() instanceof Player && getState() == IDLE && getPassengers().isEmpty()) {
            ItemStack stack = new ItemStack(ModItems.ROCKET.get());
            if (getFuel() > 0.0F) {
                stack.getOrCreateTag().putFloat("Fuel", getFuel());
            }
            spawnAtLocation(stack);
            discard();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ boucle principale

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer pilot = getPilot();
        if (inputCooldown > 0) inputCooldown--;

        boolean burning = false;
        switch (getState()) {
            case IDLE -> tickIdle(pilot);
            case COUNTDOWN -> burning = tickCountdown(serverLevel, pilot);
            case ASCENT -> burning = tickAscent(serverLevel);
            case ORBIT -> tickOrbit(serverLevel, pilot);
            case TRANSIT -> burning = tickTransit(serverLevel);
            case DESCENT -> burning = tickDescent(serverLevel);
            default -> { }
        }

        if (burning != isBurning()) {
            this.entityData.set(BURNING, burning);
        }
        if (burning) {
            exhaust(serverLevel);
        }
    }

    private ServerPlayer getPilot() {
        for (Entity e : getPassengers()) {
            if (e instanceof ServerPlayer sp) return sp;
        }
        return null;
    }

    private void announce(Component message) {
        for (Entity e : getPassengers()) {
            if (e instanceof Player p) p.displayClientMessage(message, true);
        }
    }

    private void announceNearby(Component message) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24.0D))) {
            p.displayClientMessage(message, true);
        }
    }

    private void consumeFuel(float amount) {
        this.entityData.set(FUEL, Math.max(0.0F, getFuel() - amount));
    }

    // --- Au sol

    private void tickIdle(ServerPlayer pilot) {
        setDeltaMovement(Vec3.ZERO);
        if (pilot == null || inputCooldown > 0 || pilot.zza < 0.5F) return;
        inputCooldown = 20;

        int body = getBody();
        if (getFuel() < LAUNCH_COST[body]) {
            pilot.displayClientMessage(Component.translatable("msg.astralis.rocket_fuel_low",
                    Math.round(LAUNCH_COST[body]), Math.round(getFuel())), true);
            return;
        }
        BlockPos above = new BlockPos(getX(), getY() + getBbHeight(), getZ());
        if (!level.canSeeSky(above)) {
            pilot.displayClientMessage(Component.translatable("msg.astralis.sky_blocked"), true);
            return;
        }
        countdown = COUNTDOWN_TICKS;
        staged = false;
        setState(COUNTDOWN);
    }

    private boolean tickCountdown(ServerLevel sl, ServerPlayer pilot) {
        setDeltaMovement(Vec3.ZERO);

        if (pilot == null || (pilot.zza < -0.5F && inputCooldown == 0)) {
            setState(IDLE);
            inputCooldown = 20;
            announceNearby(Component.translatable("msg.astralis.countdown_abort"));
            return false;
        }

        countdown--;
        if (countdown > 0 && countdown % 20 == 0) {
            int seconds = countdown / 20;
            announceNearby(Component.translatable("msg.astralis.countdown", seconds));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING, SoundSource.AMBIENT,
                    2.0F, seconds <= 3 ? 1.6F : 1.0F);
        }
        if (countdown <= 0) {
            setState(ASCENT);
            announce(Component.translatable("msg.astralis.ignition"));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 4.0F, 0.5F);
            return true;
        }
        // Dégagement de vapeur puis allumage dans les 3 dernières secondes
        if (countdown <= 60) return true;
        if (countdown <= 120) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 6, 0.8, 0.1, 0.8, 0.02);
        }
        return false;
    }

    private boolean tickAscent(ServerLevel sl) {
        int body = getBody();
        double accel = body == EARTH ? 0.012D : 0.018D;
        double vy = Math.min(getDeltaMovement().y + accel, 3.0D);
        setDeltaMovement(0.0D, vy, 0.0D);
        move(MoverType.SELF, getDeltaMovement());
        consumeFuel(LAUNCH_COST[body] / 200.0F);

        if (verticalCollision && vy > 0.2D) {
            // Obstacle au-dessus : échec du lancement
            sl.explode(this, getX(), getY() + 4.0D, getZ(), 4.0F, Explosion.BlockInteraction.NONE);
            for (Entity e : new ArrayList<>(getPassengers())) {
                e.hurt(DamageSource.explosion((LivingEntity) null), 20.0F);
            }
            discard();
            return false;
        }

        if (!staged && getY() > CEILING[body] * 0.55D) {
            staged = true;
            announce(Component.translatable("msg.astralis.staging"));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.AMBIENT, 4.0F, 0.6F);
        }
        if (getY() >= CEILING[body]) {
            enterOrbit(sl);
        }
        return true;
    }

    // --- En orbite

    private void enterOrbit(ServerLevel sl) {
        ServerLevel orbit = sl.getServer().getLevel(ModDimensions.ORBIT);
        if (orbit == null) {
            announce(Component.translatable("msg.astralis.no_orbit_dimension"));
            setState(IDLE);
            return;
        }
        RocketEntity copy = transferTo(orbit, getX(), 128.0D, getZ(), ORBIT);
        if (copy != null) {
            copy.announce(Component.translatable(copy.getBody() == EARTH
                    ? "msg.astralis.orbit_earth" : "msg.astralis.orbit_moon"));
        }
    }

    private void tickOrbit(ServerLevel sl, ServerPlayer pilot) {
        setDeltaMovement(Vec3.ZERO);
        if (pilot == null || inputCooldown > 0) return;

        if (pilot.zza > 0.5F) {
            inputCooldown = 40;
            startTransfer(pilot);
        } else if (pilot.zza < -0.5F) {
            inputCooldown = 40;
            startDescent(sl, pilot);
        }
    }

    private void startTransfer(ServerPlayer pilot) {
        int dest = 1 - getBody();
        float needed = TRANSFER_COST + LANDING_COST[dest];
        if (getFuel() < needed) {
            pilot.displayClientMessage(Component.translatable("msg.astralis.rocket_fuel_low",
                    Math.round(needed), Math.round(getFuel())), true);
            return;
        }
        transitTicks = 0;
        setProgress(0.0F);
        setState(TRANSIT);
        announce(Component.translatable(dest == MOON ? "msg.astralis.transfer_moon" : "msg.astralis.transfer_earth"));
    }

    private boolean tickTransit(ServerLevel sl) {
        setDeltaMovement(Vec3.ZERO);
        transitTicks++;
        setProgress(Math.min(1.0F, transitTicks / (float) TRANSIT_TICKS));

        boolean burn = transitTicks <= BURN_TICKS;
        if (burn) {
            consumeFuel(TRANSFER_COST / BURN_TICKS);
        }
        if (transitTicks == TRANSIT_TICKS - 240) {
            announce(Component.translatable("msg.astralis.flip"));
        }
        if (transitTicks >= TRANSIT_TICKS) {
            setBody(1 - getBody());
            setProgress(0.0F);
            transitTicks = 0;
            setState(ORBIT);
            announce(Component.translatable(getBody() == EARTH
                    ? "msg.astralis.orbit_earth" : "msg.astralis.orbit_moon"));
        }
        return burn;
    }

    private void startDescent(ServerLevel sl, ServerPlayer pilot) {
        int body = getBody();
        if (getFuel() < LANDING_COST[body]) {
            pilot.displayClientMessage(Component.translatable("msg.astralis.rocket_fuel_low",
                    Math.round(LANDING_COST[body]), Math.round(getFuel())), true);
            return;
        }
        ServerLevel dest = sl.getServer().getLevel(body == MOON ? ModDimensions.MOON : Level.OVERWORLD);
        if (dest == null) return;

        int x = padX;
        int z = padZ;
        double landY = ensurePad(dest, x, z);
        double startY = landY + (body == EARTH ? 200.0D : 130.0D);
        RocketEntity copy = transferTo(dest, x + 0.5D, startY, z + 0.5D, DESCENT);
        if (copy != null) {
            copy.landingY = landY;
            copy.announce(Component.translatable("msg.astralis.descent_start"));
        }
    }

    private boolean tickDescent(ServerLevel sl) {
        int body = getBody();
        double h = getY() - landingY;
        Vec3 m = getDeltaMovement();
        double target = -Math.min(3.0D, 0.12D + h * 0.035D);
        double vy;
        boolean thrust = true;

        if (body == MOON) {
            if (getFuel() <= 0.0F) {
                thrust = false;
                vy = m.y - 0.02D;
            } else {
                vy = m.y + (target - m.y) * 0.15D;
                consumeFuel(LANDING_COST[MOON] / 110.0F);
            }
        } else {
            // Rentrée : aérofreinage, puis rétrofusées sur les derniers blocs
            vy = m.y + (target - m.y) * 0.15D;
            if (h > 60.0D && h < 190.0D) {
                sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.0D, getZ(), 10, 0.8, 1.0, 0.8, 0.05);
            }
        }

        setDeltaMovement(0.0D, vy, 0.0D);
        move(MoverType.SELF, getDeltaMovement());

        if (isOnGround() || getY() <= landingY + 0.02D) {
            if (vy < -0.8D) {
                sl.explode(this, getX(), getY() + 1.0D, getZ(), 5.0F, Explosion.BlockInteraction.NONE);
                for (Entity e : new ArrayList<>(getPassengers())) {
                    e.hurt(DamageSource.FALL, 1000.0F);
                }
                discard();
                return false;
            }
            setPos(getX(), landingY, getZ());
            setDeltaMovement(Vec3.ZERO);
            setState(IDLE);
            announce(Component.translatable("msg.astralis.landed"));
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.AMBIENT, 2.0F, 0.5F);
            return false;
        }
        return thrust && (body == MOON || h < 50.0D);
    }

    // ------------------------------------------------------------------ utilitaires

    private void exhaust(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.FLAME, getX(), getY() - 0.3D, getZ(), 12, 0.25, 0.1, 0.25, 0.15);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() - 0.5D, getZ(), 6, 0.4, 0.1, 0.4, 0.05);
        if (tickCount % 8 == 0) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 3.0F, 0.6F);
        }
    }

    /** S'assure qu'une plateforme existe en (x, z) et renvoie la hauteur d'appui de la fusée. */
    private static double ensurePad(ServerLevel level, int x, int z) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos landing = new BlockPos(x, surface, z);
        BlockPos below = landing.below();
        BlockPos pad;
        if (level.getBlockState(below).is(ModBlocks.LAUNCH_PAD.get())) {
            pad = below;
        } else {
            pad = landing;
            level.setBlockAndUpdate(pad, ModBlocks.LAUNCH_PAD.get().defaultBlockState());
        }
        return pad.getY() + 0.25D;
    }

    /**
     * Déplace la fusée et ses passagers vers une autre dimension : crée une copie, y transfère les joueurs
     * puis supprime l'original. Renvoie la nouvelle fusée.
     */
    private RocketEntity transferTo(ServerLevel dest, double x, double y, double z, int newState) {
        RocketEntity copy = ModEntities.ROCKET.get().create(dest);
        if (copy == null) return null;

        copy.moveTo(x, y, z, getYRot(), 0.0F);
        copy.setBody(getBody());
        copy.setFuel(getFuel());
        copy.setProgress(0.0F);
        copy.setPad(padX, padZ);
        copy.landingY = landingY;
        copy.staged = true;
        copy.setState(newState);
        copy.transferring = true;

        List<Entity> riders = new ArrayList<>(getPassengers());
        this.transferring = true;
        for (Entity rider : riders) {
            rider.stopRiding();
        }
        dest.addFreshEntity(copy);

        final Vec3 arrival = new Vec3(x, y + 5.0D, z);
        for (Entity rider : riders) {
            if (rider instanceof ServerPlayer sp) {
                sp.changeDimension(dest, new ITeleporter() {
                    @Override
                    public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld,
                                                    Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                        return new PortalInfo(arrival, Vec3.ZERO, entity.getYRot(), entity.getXRot());
                    }

                    @Override
                    public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
                        return false;
                    }
                });
                sp.startRiding(copy, true);
            }
        }
        copy.transferring = false;
        this.discard();
        return copy;
    }
}
