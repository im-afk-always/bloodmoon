package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.mixin.CreeperAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

/**
 * Creeper eléctrico maldito: siempre cargado (aura roja, ver CursedCreeperRenderer),
 * explosión x20 respecto a un creeper normal y fuego al explotar (CreeperMixin).
 */
public class CursedCreeper extends Creeper {
    private static final double BAR_RANGE = 48.0;
    private static final int VANILLA_RADIUS = 3;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.bloodmoon.cursed_creeper").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    public CursedCreeper(EntityType<? extends Creeper> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        // Al estar cargado, vanilla duplica el radio: base = 3 * mult / 2 -> radio final = 3 * mult.
        int base = Math.max(1, Math.min(127, Math.round(VANILLA_RADIUS * BloodMoonConfig.CURSED_EXPLOSION_MULT.get() / 2F)));
        ((CreeperAccessor) this).bloodmoon$setExplosionRadius(base);
    }

    @Override
    public boolean isPowered() {
        return true;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BossBars.update(this, bossEvent, BAR_RANGE);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }
}
