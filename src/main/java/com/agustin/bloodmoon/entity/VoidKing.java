package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.invasion.VoidAllies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rey del Vacío: cúspide del Dominio (fase Trono). Flota en su coliseo, ~6,5 bloques.
 * <ul>
 *   <li>Cetro: golpe aplastante con onda corta alrededor del objetivo.</li>
 *   <li>Lluvia del Juicio: lágrimas del cielo (sin cráter) sobre el objetivo; en fase 2, tres.</li>
 *   <li>Decreto Real: invoca la guardia (Centinelas y Arqueros de nivel 8) y enardece a todo aliado cercano.</li>
 *   <li>Nova de la Corona: tres anillos de choque que se expanden (6, 12 y 18 bloques). Hay que saltarlos o alejarse.</li>
 *   <li>Por debajo de la mitad: fase 2, todo más seguido.</li>
 * </ul>
 */
public class VoidKing extends Monster {
    public static final int NONE = 0, JUDGEMENT = 1, DECREE = 2, NOVA = 3;
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(VoidKing.class, EntityDataSerializers.BYTE);

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.bloodmoon.void_king"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);
    private boolean dominionBound, phaseTwoAnnounced;
    private int actionTick, judgementCd = 80, decreeCd = 260, novaCd = 160;
    /** Llamado a las armas: enfriamiento, oleadas pendientes y a quién defienden. */
    private int callCd, wavesLeft, waveTimer, wavePer;
    private java.util.UUID assassin;
    /** Cliente: tick en que empezó la acción actual (para animar). */
    public float actionStart;

    public VoidKing(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 1000;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1200.0)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 22.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, (byte) NONE);
    }

    public int getAction() {
        return entityData.get(DATA_ACTION);
    }

    private void setAction(int a) {
        entityData.set(DATA_ACTION, (byte) a);
        actionTick = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key)) actionStart = tickCount;
    }

    public void bindToDominion() {
        this.dominionBound = true;
    }

    @Override
    public boolean shouldBeSaved() {
        return !dominionBound && super.shouldBeSaved();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected AABB getAttackBoundingBox() {
        return super.getAttackBoundingBox().inflate(1.5, 0, 1.5);
    }

    private boolean phaseTwo() {
        return getHealth() < getMaxHealth() * 0.5F;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypeTags.IS_FALL)
                || super.isInvulnerableTo(source);
    }

    /** Un jugador se atrevió a atacar al Rey: llama a las armas. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && level() instanceof ServerLevel sl && source.getEntity() instanceof Player p && !p.isCreative() && !p.isSpectator()
                && callCd <= 0) {
            callToArms(sl, p);
        }
        return hit;
    }

    /**
     * Toda la horda acude: los soldados del Vacío cercanos (96 bloques) van por el agresor y llegan refuerzos en tres
     * oleadas desde portales alrededor del Rey. Los refuerzos salen de las tropas de la horda (se descuentan).
     */
    private void callToArms(ServerLevel sl, Player p) {
        callCd = 1200;
        assassin = p.getUUID();
        com.agustin.bloodmoon.invasion.Faction f = com.agustin.bloodmoon.invasion.InvasionManager.factionOf(this);
        int lv = f == null ? 5 : com.agustin.bloodmoon.invasion.InvasionManager.hordeLevel(f);
        int want = 8 + 2 * lv;
        int n = f == null ? want : Math.min(want, com.agustin.bloodmoon.invasion.InvasionManager.levyAvailable(f));
        if (f != null && n > 0) com.agustin.bloodmoon.invasion.InvasionManager.spendLevy(sl, f, n);
        wavesLeft = n > 0 ? 3 : 0;
        wavePer = (n + 2) / 3;
        waveTimer = 20;
        int rallied = 0;
        for (net.minecraft.world.entity.Mob m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(96),
                m -> m != this && VoidAllies.isVoid(m))) {
            m.setTarget(p);
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
            rallied++;
        }
        Component msg = Component.translatable(n > 0 ? "bloodmoon.void_king.call" : "bloodmoon.void_king.call_empty", getDisplayName())
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);
        for (ServerPlayer q : sl.players()) {
            if (q.distanceToSqr(this) > 160 * 160) continue;
            q.sendSystemMessage(msg);
            q.playNotifySound(SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.HOSTILE, 2F, 0.5F);
            q.playNotifySound(SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.HOSTILE, 0.7F, 0.5F);
        }
        sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 5, getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 3, getZ(), 300, 6, 3, 6, 0.3);
    }

    private void tickCallToArms(ServerLevel sl) {
        if (callCd > 0) callCd--;
        if (wavesLeft <= 0 || --waveTimer > 0) return;
        waveTimer = 60;
        wavesLeft--;
        LivingEntity target = assassin == null ? null : sl.getPlayerByUUID(assassin);
        if (target == null || !target.isAlive()) target = getTarget();
        com.agustin.bloodmoon.invasion.Faction f = com.agustin.bloodmoon.invasion.InvasionManager.factionOf(this);
        int lv = f == null ? 5 : com.agustin.bloodmoon.invasion.InvasionManager.hordeLevel(f);
        int captains = 0;
        for (int i = 0; i < wavePer; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 6 + random.nextDouble() * 7;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            float roll = random.nextFloat();
            net.minecraft.world.entity.Mob m;
            if (roll < 0.15F) {
                VoidMage mage = ModEntities.VOID_MAGE.get().create(sl);
                if (mage == null) continue;
                mage.applyLevel(lv);
                mage.bindToDominion();
                m = mage;
            } else {
                boolean captain = roll > 0.9F && captains < 1;
                if (captain) captains++;
                VoidSkeleton s = (captain ? ModEntities.VOID_CAPTAIN.get() : roll < 0.4F ? ModEntities.VOID_ARCHER.get()
                        : ModEntities.VOID_SENTINEL.get()).create(sl);
                if (s == null) continue;
                m = s;
            }
            m.moveTo(x, y, z, random.nextFloat() * 360F, 0F);
            m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            if (m instanceof VoidSkeleton s) {
                s.equipForTier(lv);
                s.bindToDominion();
            }
            if (target != null) m.setTarget(target);
            sl.addFreshEntity(m);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 40, 0.3, 1, 0.3, 0.08);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.2, z, 12, 0.4, 0.05, 0.4, 0.02);
        }
        playSound(SoundEvents.EVOKER_CAST_SPELL, 3F, 0.5F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl) {
            // el cetro cae y la tierra tiembla alrededor del golpeado
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3, 1, 3),
                    e -> e != this && e != target && e.isAlive() && !VoidAllies.isVoid(e))) {
                e.hurt(damageSources().mobAttack(this), 8F);
            }
            sl.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.5, target.getZ(), 2, 0.6, 0.2, 0.6, 0);
            sl.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 0.5, target.getZ(), 30, 1.2, 0.3, 1.2, 0.1);
            playSound(SoundEvents.ANVIL_LAND, 1.8F, 0.5F);
            target.push(0, 0.5, 0);
            target.hurtMarked = true;
        }
        return hit;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BossBars.update(this, bossEvent, 96.0);
        if (!(level() instanceof ServerLevel sl)) return;
        tickCallToArms(sl);
        if (judgementCd > 0) judgementCd--;
        if (decreeCd > 0) decreeCd--;
        if (novaCd > 0) novaCd--;
        if (phaseTwo() && !phaseTwoAnnounced) {
            phaseTwoAnnounced = true;
            playSound(SoundEvents.WITHER_SPAWN, 4F, 0.6F);
            sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 4, getZ(), 1, 0, 0, 0, 0);
            Component msg = Component.translatable("bloodmoon.void_king.phase2", getDisplayName());
            for (ServerPlayer p : sl.players()) if (p.distanceToSqr(this) < 96 * 96) p.sendSystemMessage(msg);
            if (getTarget() instanceof Player tp) {   // herido de muerte: vuelve a llamar a las armas
                callCd = 0;
                callToArms(sl, tp);
            }
        }
        LivingEntity target = getTarget();
        int action = getAction();
        if (action != NONE) {
            getNavigation().stop();
            actionTick++;
            switch (action) {
                case JUDGEMENT -> {
                    if (actionTick == 16) judgement(sl, target);
                    if (actionTick >= 34) setAction(NONE);
                }
                case DECREE -> {
                    if (actionTick == 18) decree(sl);
                    if (actionTick >= 40) setAction(NONE);
                }
                case NOVA -> {
                    if (actionTick == 12) nova(sl, 6);
                    if (actionTick == 20) nova(sl, 12);
                    if (actionTick == 28) nova(sl, 18);
                    if (actionTick >= 40) setAction(NONE);
                }
                default -> setAction(NONE);
            }
            return;
        }
        if (target == null || !target.isAlive()) return;
        double dist = distanceTo(target);
        boolean p2 = phaseTwo();
        if (novaCd <= 0 && dist < 9) {
            setAction(NOVA);
            novaCd = p2 ? 180 : 280;
            playSound(SoundEvents.BEACON_POWER_SELECT, 3F, 0.5F);
        } else if (judgementCd <= 0 && dist < 48) {
            setAction(JUDGEMENT);
            judgementCd = p2 ? 140 : 240;
            playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 3F, 0.5F);
        } else if (decreeCd <= 0) {
            setAction(DECREE);
            decreeCd = p2 ? 480 : 760;
            playSound(SoundEvents.RAID_HORN.value(), 4F, 0.5F);
        }
    }

    private void judgement(ServerLevel sl, LivingEntity target) {
        if (target == null) return;
        int n = phaseTwo() ? 3 : 1;
        AbyssTear.spawn(sl, this, ground(sl, target.position()), 40, false);
        for (int i = 1; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 5 + random.nextDouble() * 4;
            AbyssTear.spawn(sl, this, ground(sl, target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r)), 40 + i * 6, false);
        }
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 6.5, getZ(), 60, 0.6, 0.6, 0.6, 0.3);
    }

    private static Vec3 ground(ServerLevel sl, Vec3 p) {
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
        return new Vec3(p.x, Math.min(p.y, y), p.z);
    }

    private void decree(ServerLevel sl) {
        boolean p2 = phaseTwo();
        playSound(SoundEvents.RAVAGER_ROAR, 4F, 0.4F);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 3, getZ(), 250, 4, 3, 4, 0.2);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(32), VoidAllies::isVoid)) {
            if (e == this) continue;
            e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
            e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
        }
        int guards = p2 ? 5 : 3;
        for (int i = 0; i < guards; i++) {
            boolean archer = i % 2 == 1;
            VoidSkeleton s = (archer ? ModEntities.VOID_ARCHER.get() : ModEntities.VOID_SENTINEL.get()).create(sl);
            if (s == null) continue;
            double a = i * Math.PI * 2 / guards + random.nextDouble() * 0.5;
            double x = getX() + Math.cos(a) * 5, z = getZ() + Math.sin(a) * 5;
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            s.moveTo(x, y, z, random.nextFloat() * 360F, 0F);
            s.finalizeSpawn(sl, sl.getCurrentDifficultyAt(s.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            s.equipForTier(8);
            s.bindToDominion();
            if (getTarget() != null) s.setTarget(getTarget());
            sl.addFreshEntity(s);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 40, 0.3, 1, 0.3, 0.05);
        }
    }

    /** Anillo de choque de radio r: golpea lo que esté en la banda [r-3, r+1] y a menos de 2,5 bloques del piso del Rey. */
    private void nova(ServerLevel sl, double r) {
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 3F, 0.6F + (float) r * 0.02F);
        int pts = (int) (r * 10);
        for (int i = 0; i < pts; i++) {
            double a = i * Math.PI * 2 / pts;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            sl.sendParticles(ParticleTypes.END_ROD, x, getY() + 0.4, z, 1, 0.05, 0.05, 0.05, 0.01);
            if (i % 2 == 0) sl.sendParticles(ParticleTypes.REVERSE_PORTAL, x, getY() + 0.3, z, 2, 0.1, 0.2, 0.1, 0.03);
        }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 1, 3, r + 1),
                e -> e != this && e.isAlive() && !VoidAllies.isVoid(e))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d < r - 3 || d > r + 1 || e.getY() - getY() > 2.5) continue;
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            e.hurt(damageSources().indirectMagic(this, this), 14F);
            d = Math.max(0.5, d);
            e.setDeltaMovement(dx / d * 1.2, 0.7, dz / d * 1.2);
            e.hurtMarked = true;
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {   // el Vacío que lo sostiene
                level().addParticle(ParticleTypes.REVERSE_PORTAL, getRandomX(0.9), getY() + random.nextDouble() * 0.4, getRandomZ(0.9), 0, 0.03, 0);
            }
            if (random.nextInt(4) == 0) {   // polvo de oro que cae de la corona
                level().addParticle(ParticleTypes.WAX_ON, getRandomX(0.4), getY() + 6.2, getRandomZ(0.4), 0, -0.05, 0);
            }
        } else if (hasEffect(com.agustin.bloodmoon.registry.ModEffects.ASTRAL_BURN)) {
            removeEffect(com.agustin.bloodmoon.registry.ModEffects.ASTRAL_BURN);
        }
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        if (name != null) bossEvent.setName(name);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.4F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // flota: no pisa
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DominionBound", dominionBound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dominionBound = tag.getBoolean("DominionBound");
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
