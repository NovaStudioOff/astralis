package com.astralis.damage;

import net.minecraft.world.damagesource.DamageSource;

/** Dégâts d'exposition au vide (décompression, hypoxie). Ignore l'armure. */
public class VacuumDamageSource extends DamageSource {
    public static final VacuumDamageSource INSTANCE = new VacuumDamageSource();

    private VacuumDamageSource() {
        super("astralis_vacuum");
        this.bypassArmor();
    }
}
