package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.invasion.VoidAllies;
import com.agustin.bloodmoon.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Hechicero del Vacío: no pelea cuerpo a cuerpo, mantiene la distancia y lanza conjuros.
 * <ul>
 *   <li><b>Lanza Astral</b>: carga un instante y dispara un rayo que quema con fuego astral.</li>
 *   <li><b>Lluvia de Estrellas</b>: marca tres círculos alrededor del objetivo; un segundo y medio después estallan.</li>
 *   <li><b>Prisión del Vacío</b>: jaula de runas que inmoviliza y debilita.</li>
 *   <li><b>Espectros</b>: invoca tres espíritus voladores que le obedecen un rato.</li>
 *   <li><b>Paso del Vacío</b>: si lo alcanzan, se teletransporta lejos.</li>
 *   <li><b>Égida</b>: herido, se envuelve en un escudo de runas.</li>
 * </ul>
 * Su poder escala con el nivel de la horda ({@link #applyLevel}).
 */
public class VoidMage extends Monster {
    public static final int NONE = 0, LANCE = 1, STARFALL = 2, PRISON = 3, SPECTERS = 4, WARD = 5;
    private static final EntityDataAccessor<Byte> DATA_SPELL = SynchedEntityData.defineId(VoidMage.class, EntityDataSerializers.BYTE);
    private static final int[] CAST = {0, 20, 30, 16, 30, 12};
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7F, 0.3F, 1F), 1.4F);

    private boolean dominionBound;
    private int hordeLevel = 0;
    private int castTick, globalCd = 40, lanceCd = 30, starCd = 120, prisonCd = 160, specterCd = 260, blinkCd = 0, wardCd = 0;
    private final Vec3[] marks = new Vec3[3];
    private LivingEntity prisoner;
    private int prisonTicks;
    /** Cliente: tick en que empezó el conjuro actual. */
    public float spellStart;

    public VoidMage(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPELL, (byte) NONE);
    }

    public int getSpell() {
        return entityData.get(DATA_SPELL);
    }

    private void setSpell(int s) {
        entityData.set(DATA_SPELL, (byte) s);
        castTick = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SPELL.equals(key)) spellStart = tickCount;
    }

    public void bindToDominion() {
        dominionBound = true;
    }

    @Override
    public boolean shouldBeSaved() {
        return !dominionBound && super.shouldBeSaved();
    }

    /** Nivel de la horda (0-10): más vida y conjuros más fuertes. */
    public void applyLevel(int lv) {
        hordeLevel = Math.max(0, Math.min(10, lv));
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(LEVEL_HP);
            hp.addPermanentModifier(new AttributeModifier(LEVEL_HP, hordeLevel * 0.12, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            setHealth(getMaxHealth());
        }
    }

    private static final net.minecraft.resources.ResourceLocation LEVEL_HP =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.agustin.bloodmoon.BloodMoonMod.MODID, "mage_level");

    private float power() {
        return 1F + hordeLevel * 0.12F;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (globalCd > 0) globalCd--;
        if (lanceCd > 0) lanceCd--;
        if (starCd > 0) starCd--;
        if (prisonCd > 0) prisonCd--;
        if (specterCd > 0) specterCd--;
        if (blinkCd > 0) blinkCd--;
        if (wardCd > 0) wardCd--;
        tickPrison(sl);
        LivingEntity t = getTarget();
        int spell = getSpell();
        if (spell != NONE) {
            getNavigation().stop();
            if (t != null) getLookControl().setLookAt(t, 30F, 30F);
            castTick++;
            castFx(sl, spell, t);
            if (castTick >= CAST[spell]) {
                release(sl, spell, t);
                setSpell(NONE);
                globalCd = 25 + random.nextInt(20);
            }
            return;
        }
        if (t == null || !t.isAlive()) return;
        double dist = distanceTo(t);
        getLookControl().setLookAt(t, 30F, 30F);
        // mantener la distancia: ni muy cerca ni muy lejos
        if (dist < 5 && blinkCd <= 0) {
            blink(sl, t);
        } else if (dist < 8) {
            Vec3 away = position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(6);
            getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.15);
        } else if (dist > 16 || !hasLineOfSight(t)) {
            getNavigation().moveTo(t, 1.0);
        } else {
            getNavigation().stop();
        }
        if (globalCd > 0 || dist > 28) return;
        if (getHealth() < getMaxHealth() * 0.5F && wardCd <= 0) start(sl, WARD);
        else if (specterCd <= 0 && dist < 20) start(sl, SPECTERS);
        else if (prisonCd <= 0 && dist < 18 && hasLineOfSight(t)) start(sl, PRISON);
        else if (starCd <= 0 && dist < 24) start(sl, STARFALL);
        else if (lanceCd <= 0 && hasLineOfSight(t)) start(sl, LANCE);
    }

    private void start(ServerLevel sl, int spell) {
        setSpell(spell);
        LivingEntity t = getTarget();
        switch (spell) {
            case LANCE -> lanceCd = 50 + random.nextInt(30);
            case STARFALL -> {
                starCd = 200;
                for (int i = 0; i < 3; i++) {
                    Vec3 base = t == null ? position() : t.position();
                    double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : 2.5 + random.nextDouble() * 2;
                    double x = base.x + Math.cos(a) * r, z = base.z + Math.sin(a) * r;
                    int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                    marks[i] = new Vec3(x, Math.min(y, base.y + 3), z);
                }
            }
            case PRISON -> prisonCd = 280;
            case SPECTERS -> specterCd = 420;
            case WARD -> wardCd = 400;
            default -> { }
        }
        playSound(spell == SPECTERS ? SoundEvents.EVOKER_PREPARE_SUMMON : SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.5F, 0.7F);
    }

    /** Efectos mientras carga el conjuro. */
    private void castFx(ServerLevel sl, int spell, LivingEntity t) {
        Vec3 hand = staffTip();
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, hand.x, hand.y, hand.z, 3, 0.15, 0.15, 0.15, 0.02);
        sl.sendParticles(VIOLET, getX(), getY() + 0.1, getZ(), 2, 0.6, 0.05, 0.6, 0);
        if (spell == STARFALL) {
            for (Vec3 m : marks) {
                if (m == null) continue;
                for (int k = 0; k < 10; k++) {
                    double a = k * Math.PI / 5 + castTick * 0.1;
                    sl.sendParticles(VIOLET, m.x + Math.cos(a) * 2.2, m.y + 0.15, m.z + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
                }
                sl.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 6 - castTick * 0.15, m.z, 1, 0.1, 0.1, 0.1, 0);
            }
        } else if (spell == WARD) {
            sphere(sl, 1.3, 14);
        } else if (spell == PRISON && t != null) {
            sl.sendParticles(ParticleTypes.WITCH, t.getX(), t.getY() + 1, t.getZ(), 4, 0.5, 0.8, 0.5, 0);
        }
    }

    private void release(ServerLevel sl, int spell, LivingEntity t) {
        switch (spell) {
            case LANCE -> lance(sl, t);
            case STARFALL -> starfall(sl);
            case PRISON -> {
                if (t == null) return;
                prisoner = t;
                prisonTicks = 60;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4), this);
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
                t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
                t.hurt(damageSources().indirectMagic(this, this), 3F * power());
                playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.6F, 0.6F);
            }
            case SPECTERS -> specters(sl, t);
            case WARD -> {
                addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1));
                addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
                sphere(sl, 1.4, 60);
                playSound(SoundEvents.BEACON_POWER_SELECT, 1.4F, 1.4F);
            }
            default -> { }
        }
    }

    private Vec3 staffTip() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(getX() - Mth.cos(yaw) * 0.45 - Mth.sin(yaw) * 0.3, getY() + 2.2, getZ() - Mth.sin(yaw) * 0.45 + Mth.cos(yaw) * 0.3);
    }

    private void lance(ServerLevel sl, LivingEntity t) {
        if (t == null) return;
        Vec3 from = staffTip(), to = t.getEyePosition().add(0, -0.3, 0);
        Vec3 d = to.subtract(from);
        int n = (int) (d.length() * 4);
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            sl.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : VIOLET, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.4F, 1.5F);
        if (!hasLineOfSight(t)) return;
        t.hurt(damageSources().indirectMagic(this, this), 6F * power());
        t.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 60, 0), this);
    }

    private void starfall(ServerLevel sl) {
        for (Vec3 m : marks) {
            if (m == null) continue;
            sl.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 2, 0.5, 0.3, 0.5, 0);
            sl.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 0.5, m.z, 40, 0.3, 0.3, 0.3, 0.25);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, m.x, m.y + 0.3, m.z, 40, 1.5, 0.2, 1.5, 0.1);
            sl.playSound(null, BlockPos.containing(m), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.3F);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(m, m).inflate(2.5, 2, 2.5),
                    e -> e.isAlive() && !VoidAllies.isVoid(e) && !(e instanceof Vex))) {
                e.hurt(damageSources().indirectMagic(this, this), 7F * power());
                Vec3 push = e.position().subtract(m).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) push = push.normalize();
                e.push(push.x * 0.6, 0.5, push.z * 0.6);
                e.hurtMarked = true;
            }
        }
    }

    private void specters(ServerLevel sl, LivingEntity t) {
        playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5F, 0.6F);
        int n = 2 + (hordeLevel >= 5 ? 1 : 0);
        for (int i = 0; i < n; i++) {
            Vex vex = EntityType.VEX.create(sl);
            if (vex == null) continue;
            vex.moveTo(getX() + random.nextGaussian(), getY() + 1.5, getZ() + random.nextGaussian(), 0F, 0F);
            vex.finalizeSpawn(sl, sl.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            vex.setOwner(this);
            vex.setBoundOrigin(blockPosition());
            vex.setLimitedLife(20 * (15 + random.nextInt(10)));
            if (t != null) vex.setTarget(t);
            sl.addFreshEntity(vex);
            sl.sendParticles(ParticleTypes.SOUL, vex.getX(), vex.getY(), vex.getZ(), 15, 0.3, 0.3, 0.3, 0.05);
        }
    }

    private void blink(ServerLevel sl, LivingEntity t) {
        for (int tries = 0; tries < 12; tries++) {
            double a = random.nextDouble() * Math.PI * 2, r = 9 + random.nextDouble() * 5;
            double x = t.getX() + Math.cos(a) * r, z = t.getZ() + Math.sin(a) * r;
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(y - getY()) > 8) continue;
            Vec3 old = position();
            if (randomTeleport(x, y, z, true)) {
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, old.x, old.y + 1, old.z, 40, 0.3, 0.8, 0.3, 0.1);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 40, 0.3, 0.8, 0.3, 0.1);
                playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 0.8F);
                blinkCd = 140;
                return;
            }
        }
        blinkCd = 40;
    }

    private void tickPrison(ServerLevel sl) {
        if (prisoner == null) return;
        if (--prisonTicks <= 0 || !prisoner.isAlive()) {
            prisoner = null;
            return;
        }
        double x = prisoner.getX(), y = prisoner.getY(), z = prisoner.getZ();
        if (prisonTicks % 2 == 0) {
            for (int k = 0; k < 8; k++) {   // barrotes de runas
                double a = k * Math.PI / 4 + prisonTicks * 0.05;
                for (double h = 0; h < 2.4; h += 0.6) sl.sendParticles(VIOLET, x + Math.cos(a) * 0.9, y + h, z + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
            }
        }
    }

    private void sphere(ServerLevel sl, double r, int n) {
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(r);
            sl.sendParticles(VIOLET, getX() + d.x, getY() + 1.1 + d.y, getZ() + d.z, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.WITCH, getRandomX(0.6), getY() + random.nextDouble() * 2, getRandomZ(0.6), 0, 0.02, 0);
        } else if (!level().isClientSide && hasEffect(ModEffects.ASTRAL_BURN)) {
            removeEffect(ModEffects.ASTRAL_BURN);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ILLUSIONER_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ILLUSIONER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SKELETON_STEP, 0.4F, 0.7F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DominionBound", dominionBound);
        tag.putInt("HordeLevel", hordeLevel);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dominionBound = tag.getBoolean("DominionBound");
        hordeLevel = tag.getInt("HordeLevel");
    }
}
