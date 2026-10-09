package com.agustin.bloodmoon;

import com.agustin.bloodmoon.network.OfferingPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * La ofrenda a la deidad de la cosecha. En cada Luna de la Cosecha hay que matar mobs hostiles: días transcurridos x 1,5
 * (redondeo hacia arriba). La cuenta es compartida por todos los jugadores.
 * <ul>
 *   <li>Cada ofrenda puede traer una bendición: Fuerza, Resistencia, Velocidad o Regeneración (I-III) por 20 s.</li>
 *   <li>Matar un mob no hostil (un pollo, un aldeano...) suma 2 ofrendas a la cuenta: "ofrenda deshonrosa".</li>
 *   <li>Saltear la luna durmiendo, o llegar al amanecer sin completar, ofende a la deidad.</li>
 *   <li>3 ofensas: la próxima luna no deja dormir. Al terminar esa luna, la cuenta de ofensas vuelve a cero.</li>
 * </ul>
 */
public final class OfferingManager {
    public static final int MAX_OFFENSES = 3;
    private static final int BLESSING_TICKS = 20 * 20;

    private OfferingManager() {}

    private static int requiredFor(ServerLevel overworld) {
        long days = overworld.getDayTime() / 24000L;
        int req = (int) Math.ceil(days * BloodMoonConfig.OFFERING_PER_DAY.get());
        int cap = BloodMoonConfig.OFFERING_CAP.get();
        if (cap > 0) req = Math.min(req, cap);
        return Math.max(1, req);
    }

    // ---------------------------------------------------------------- inicio y fin de la luna

    public static void begin(ServerLevel overworld) {
        BloodMoonData data = BloodMoonData.get(overworld);
        boolean unskippable = data.getOffenses() >= MAX_OFFENSES;
        int required = requiredFor(overworld);
        data.startOffering(required, unskippable);
        DevotionManager.onOfferingBegin(overworld);
        tell(overworld, Component.translatable("bloodmoon.offering.start", required).withStyle(ChatFormatting.GOLD));
        if (unskippable) {
            tell(overworld, Component.translatable("bloodmoon.offering.unskippable").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            for (ServerPlayer p : overworld.players()) p.playNotifySound(SoundEvents.WITHER_AMBIENT, SoundSource.AMBIENT, 1F, 0.5F);
        }
        broadcast(overworld);
    }

    /** Amanecer: si la ofrenda quedó incompleta (y no se castigó ya por dormir), la deidad se ofende. */
    public static void end(ServerLevel overworld) {
        BloodMoonData data = BloodMoonData.get(overworld);
        if (!data.isOfferingActive()) return;
        boolean wasUnskippable = data.isOfferingUnskippable();
        if (!data.isOfferingSettled() && !data.isOfferingComplete()) {
            offend(overworld, data, "bloodmoon.offering.incomplete");
        }
        if (wasUnskippable) {
            data.setOffenses(0);   // la luna sin descanso ya fue el castigo
            tell(overworld, Component.translatable("bloodmoon.offering.forgiven").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        data.stopOffering();
        broadcast(overworld);
    }

    private static void offend(ServerLevel overworld, BloodMoonData data, String key) {
        data.setOffenses(data.getOffenses() + 1);
        data.setOfferingSettled();
        int n = data.getOffenses();
        tell(overworld, Component.translatable(key).withStyle(ChatFormatting.DARK_RED));
        if (n >= MAX_OFFENSES) {
            tell(overworld, Component.translatable("bloodmoon.offering.warning").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        } else {
            tell(overworld, Component.translatable("bloodmoon.offering.offenses", n, MAX_OFFENSES).withStyle(ChatFormatting.GRAY));
        }
        for (ServerPlayer p : overworld.players()) p.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1F, 0.6F);
    }

    // ---------------------------------------------------------------- ofrendas

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (victim instanceof Player) return;
        BloodMoonData data = BloodMoonData.get(level);
        if (!data.isOfferingActive() || data.isOfferingComplete()) return;

        if (victim instanceof Enemy) {
            data.addOfferingDone(1);
            DevotionManager.onOfferingKill(level, killer);
            if (killer.getRandom().nextDouble() < BloodMoonConfig.OFFERING_BLESSING_CHANCE.get()) bless(killer);
            if (data.isOfferingComplete()) {
                data.setOfferingSettled();
                tell(level, Component.translatable("bloodmoon.offering.complete").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                for (ServerPlayer p : level.players()) {
                    p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8F, 0.8F);
                }
                // la reputación se mide por el pedido original: las ofrendas deshonrosas no la inflan
                DevotionManager.onRequestComplete(level, Deity.HARVEST, requiredFor(level));
            }
            broadcast(level);
        } else if (victim instanceof Mob) {
            data.addOfferingRequired(2);
            tell(level, Component.translatable("bloodmoon.offering.dishonor").withStyle(ChatFormatting.DARK_RED));
            killer.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6F, 0.7F);
            broadcast(level);
        }
    }

    private static void bless(ServerPlayer player) {
        RandomSource r = player.getRandom();
        Holder<MobEffect>[] effects = blessings();
        Holder<MobEffect> effect = effects[r.nextInt(effects.length)];
        float roll = r.nextFloat();
        int amp = roll < 0.5F ? 0 : roll < 0.85F ? 1 : 2;
        player.addEffect(new MobEffectInstance(effect, BLESSING_TICKS, amp, false, true, true));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.2F + amp * 0.2F);
    }

    @SuppressWarnings("unchecked")
    private static Holder<MobEffect>[] blessings() {
        return new Holder[]{MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE, MobEffects.MOVEMENT_SPEED, MobEffects.REGENERATION};
    }

    // ---------------------------------------------------------------- dormir

    /** La luna sin descanso no deja dormir. */
    public static void onCanSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        BloodMoonData data = BloodMoonData.get(level);
        if (data.isOfferingActive() && data.isOfferingUnskippable()) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("bloodmoon.offering.nosleep").withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /** Saltearse la luna durmiendo con la ofrenda incompleta ofende a la deidad (se avisa al despertar). */
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        BloodMoonData data = BloodMoonData.get(level);
        if (!data.isOfferingActive() || data.isOfferingSettled() || data.isOfferingComplete()) return;
        offend(level, data, "bloodmoon.offering.skipped");
    }

    // ---------------------------------------------------------------- sincronización

    private static void tell(ServerLevel level, Component msg) {
        for (ServerPlayer p : level.players()) p.sendSystemMessage(msg);
    }

    public static void broadcast(ServerLevel overworld) {
        PacketDistributor.sendToPlayersInDimension(overworld, payload(BloodMoonData.get(overworld)));
    }

    private static OfferingPayload payload(BloodMoonData data) {
        return new OfferingPayload(data.isOfferingActive(), data.getOfferingDone(), data.getOfferingRequired(), data.isOfferingUnskippable());
    }

    private static void sync(ServerPlayer player, boolean inOverworld) {
        BloodMoonData data = BloodMoonData.get(player.server.overworld());
        PacketDistributor.sendToPlayer(player, inOverworld ? payload(data) : new OfferingPayload(false, 0, 0, false));
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, player.level().dimension() == Level.OVERWORLD);
    }

    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, event.getTo() == Level.OVERWORLD);
    }
}
