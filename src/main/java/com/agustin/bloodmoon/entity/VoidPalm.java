package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.registry.ModSounds;
import com.agustin.bloodmoon.world.SurfaceBlast;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * La Palma del Vacío: al llegar a la mitad de su vida, el Observador Desatado materializa en el cielo una mano de
 * energía de 100 bloques que desciende durante 30 segundos (contador en pantalla). Mientras baja, la pantalla se
 * oscurece; al tocar el suelo brota de golpe una luz cegadora y estalla como una supernova: la onda se esparce por la superficie arrasándolo todo hasta 150 bloques, sin abrir agujeros al vacío.
 */
public class VoidPalm extends Entity {
    /**
     * La pantalla se oscurece durante los últimos DARKEN ticks del descenso; toca el piso en DESCEND, la luz brota del
     * impacto durante FLARE ticks y estalla en BLAST.
     */
    public static final int DESCEND = 600, DARKEN = 300, FLARE = 8, BLAST = DESCEND + FLARE, END = BLAST + 60;
    public static final float START_H = 210F, BLAST_R = 150F, LETHAL_R = 75F;

    private static final EntityDataAccessor<Vector3f> DATA_GROUND = SynchedEntityData.defineId(VoidPalm.class, EntityDataSerializers.VECTOR3);

    private int ownerId = -1;

    public VoidPalm(EntityType<? extends VoidPalm> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static VoidPalm summon(ServerLevel level, Entity owner, Vec3 ground) {
        VoidPalm p = ModEntities.VOID_PALM.get().create(level);
        if (p == null) return null;
        p.ownerId = owner == null ? -1 : owner.getId();
        p.entityData.set(DATA_GROUND, new Vector3f((float) ground.x, (float) ground.y, (float) ground.z));
        p.moveTo(ground.x, ground.y + START_H, ground.z, 0F, 0F);
        level.addFreshEntity(p);
        Component msg = Component.translatable("bloodmoon.palm.warning").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        var payload = new com.agustin.bloodmoon.network.PalmPayload(com.agustin.bloodmoon.network.PalmPayload.START, ground.x, ground.y, ground.z);
        for (ServerPlayer pl : level.players()) {
            if (pl.distanceToSqr(ground) < 500 * 500) {
                pl.displayClientMessage(msg, false);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(pl, payload);
            }
        }
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_GROUND, new Vector3f());
    }

    public Vec3 ground() {
        Vector3f v = entityData.get(DATA_GROUND);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Altura de la palma sobre el suelo según la edad (desciende acelerando). */
    public static float height(float age) {
        if (age >= DESCEND) return 0F;
        float k = age / DESCEND;
        return START_H * (1F - k) * (1F - 0.35F * k);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 g = ground();
        if (!(level() instanceof ServerLevel sl)) {
            if (tickCount < DESCEND && random.nextInt(2) == 0) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 50;
                level().addAlwaysVisibleParticle(ParticleTypes.REVERSE_PORTAL, true, g.x + Math.cos(a) * r, g.y + 0.5, g.z + Math.sin(a) * r, 0, 0.4, 0);
            }
            return;
        }
        int t = tickCount;
        setPos(g.x, g.y + height(t), g.z);
        if (t == 1) {
            sound(sl, ModSounds.EYE_AWAKEN.get(), 0.35F);
            sound(sl, ModSounds.EYE_SCREAM.get(), 0.3F);
        }
        if (t < DESCEND && t % 100 == 50) sound(sl, SoundEvents.WARDEN_HEARTBEAT, 0.3F);
        if (t == DESCEND - DARKEN) sound(sl, SoundEvents.BEACON_DEACTIVATE, 0.4F);
        if (t == DESCEND - 100) sound(sl, ModSounds.EYE_CHARGE.get(), 0.3F);
        if (t == DESCEND - 50) sound(sl, ModSounds.SUPERNOVA_CHARGE.get(), 0.6F);
        if (t == DESCEND) {
            sound(sl, ModSounds.EYE_IMPLODE.get(), 0.6F);
            sound(sl, SoundEvents.BEACON_ACTIVATE, 0.5F);
        }
        if (t == BLAST) blast(sl, g);
        if (t >= END) discard();
    }

    private void blast(ServerLevel sl, Vec3 g) {
        sound(sl, ModSounds.SUPERNOVA_BLAST.get(), 0.7F);
        sound(sl, SoundEvents.GENERIC_EXPLODE.value(), 0.2F);
        Entity owner = sl.getEntity(ownerId);
        AABB box = new AABB(g, g).inflate(BLAST_R, 80, BLAST_R);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof VoidEye))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double dx = e.getX() - g.x, dz = e.getZ() - g.z, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            if (d > BLAST_R) continue;
            double k = d < LETHAL_R ? 1 : 1 - (d - LETHAL_R) / (BLAST_R - LETHAL_R);
            e.hurt(damageSources().indirectMagic(this, owner != null ? owner : this), (float) (8 + 92 * k * k));
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
            e.setDeltaMovement(dx / d * (1.2 + 2.4 * k), 0.6 + 1.2 * k, dz / d * (1.2 + 2.4 * k));
            e.hurtMarked = true;
        }
        if (sl.dimension() == ModDimensions.BEYOND || sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            SurfaceBlast.start(sl, BlockPos.containing(g.x, g.y - 1, g.z), BLAST_R, 6F);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, g.x, g.y + 2, g.z, 12, 12, 2, 12, 0);
        sl.sendParticles(ParticleTypes.END_ROD, g.x, g.y + 2, g.z, 400, 3, 3, 3, 3.5);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, g.x, g.y + 2, g.z, 400, 30, 2, 30, 1.5);
    }

    private void sound(ServerLevel sl, net.minecraft.sounds.SoundEvent s, float pitch) {
        Vec3 g = ground();
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(g) < 500 * 500) p.playNotifySound(s, SoundSource.HOSTILE, 3F, pitch);
        }
    }

    /** Se deshace (el jefe murió): avisa a los clientes para que quiten el contador. */
    public void cancel(ServerLevel sl) {
        Vec3 g = ground();
        var payload = new com.agustin.bloodmoon.network.PalmPayload(com.agustin.bloodmoon.network.PalmPayload.CANCEL, g.x, g.y, g.z);
        for (ServerPlayer pl : sl.players()) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(pl, payload);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
