package com.agustin.bloodmoon;

import com.agustin.bloodmoon.network.DevotionPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

/**
 * Culto a las deidades. Completar el pedido de una deidad da reputación a quienes aportaron (según su parte), y la
 * reputación sube el rango de adoración (Iniciado ... Elegido). Quien no es devoto recibe la propuesta en el chat:
 * "¿Quieres ser devoto de la Luna de la Cosecha? [SÍ] [NO]". Al aceptar entra como Iniciado, con reputación 0.
 */
public final class DevotionManager {
    /** Comando sin permisos que ejecutan los botones [SÍ]/[NO] del chat. */
    public static final String ROOT = "bloodmoon_devotion";

    private DevotionManager() {}

    // ---------------------------------------------------------------- ofrendas

    public static void onOfferingBegin(ServerLevel overworld) {
        DevotionData.get(overworld).clearContributions();
    }

    public static void onOfferingKill(ServerLevel overworld, ServerPlayer killer) {
        DevotionData.get(overworld).addContribution(killer.getUUID());
    }

    /**
     * Pedido cumplido. Cada aportante gana entre el 50 % (si aportó casi nada) y el 100 % (si lo hizo todo) de la
     * dificultad del pedido. Los no devotos (o devotos de otra deidad) reciben la propuesta de unirse.
     */
    public static void onRequestComplete(ServerLevel overworld, Deity deity, int difficulty) {
        DevotionData data = DevotionData.get(overworld);
        Map<UUID, Integer> contrib = data.contributions();
        int total = contrib.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return;
        for (Map.Entry<UUID, Integer> c : contrib.entrySet()) {
            double share = c.getValue() / (double) total;
            int rep = Math.max(1, (int) Math.round(difficulty * (0.5 + 0.5 * share)));
            ServerPlayer player = overworld.getServer().getPlayerList().getPlayer(c.getKey());
            DevotionData.Entry e = data.entry(c.getKey());
            if (e.deity == deity) {
                addReputation(overworld, c.getKey(), player, rep);
            } else if (player != null) {
                offer(overworld, player, deity);
            }
        }
        data.clearContributions();
    }

    // ---------------------------------------------------------------- reputación y rangos

    public static void addReputation(ServerLevel overworld, UUID id, ServerPlayer player, int amount) {
        DevotionData data = DevotionData.get(overworld);
        DevotionData.Entry e = data.entry(id);
        if (e.deity == null || amount == 0) return;
        DevotionRank before = DevotionRank.of(e.reputation);
        e.reputation = Math.max(0, e.reputation + amount);
        data.setDirty();
        DevotionRank after = DevotionRank.of(e.reputation);
        if (player == null) return;
        Style color = deityStyle(e.deity);
        player.sendSystemMessage(Component.translatable(amount > 0 ? "bloodmoon.devotion.gain" : "bloodmoon.devotion.loss",
                Math.abs(amount), e.deity.inSentence(), e.reputation).withStyle(ChatFormatting.GRAY));
        if (after.ordinal() > before.ordinal()) {
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.rankup", after.displayName(), e.deity.inSentence())
                    .withStyle(color.withBold(true)));
            title(player, after.displayName().copy().withStyle(color), Component.translatable("bloodmoon.devotion.rankup.sub",
                    after.roman(), e.deity.displayName()).withStyle(ChatFormatting.GRAY));
            player.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1F, 0.7F);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.6F);
        }
        sync(player);
    }

    // ---------------------------------------------------------------- propuesta, aceptar, rechazar

    public static void offer(ServerLevel overworld, ServerPlayer player, Deity deity) {
        DevotionData data = DevotionData.get(overworld);
        DevotionData.Entry e = data.entry(player.getUUID());
        if (e.deity == deity) return;
        e.pendingOffer = deity;
        data.setDirty();

        Style color = deityStyle(deity);
        MutableComponent yes = Component.literal("[").append(Component.translatable("bloodmoon.devotion.yes")).append("]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ROOT + " accept " + deity.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("bloodmoon.devotion.yes.hover", deity.inSentence()))));
        MutableComponent no = Component.literal("[").append(Component.translatable("bloodmoon.devotion.no")).append("]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ROOT + " decline " + deity.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("bloodmoon.devotion.no.hover"))));
        MutableComponent msg = Component.translatable("bloodmoon.devotion.offer", deity.inSentence()).withStyle(color.withItalic(true))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)).append(yes).append(" ").append(no);
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(msg);
        if (e.deity != null) {
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.offer.switch", e.deity.inSentence())
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 0.5F);
    }

    private static int accept(ServerPlayer player, Deity deity) {
        ServerLevel overworld = player.server.overworld();
        DevotionData data = DevotionData.get(overworld);
        DevotionData.Entry e = data.entry(player.getUUID());
        if (e.pendingOffer != deity) {
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.nooffer").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        e.pendingOffer = null;
        e.deity = deity;
        e.reputation = 0;
        data.setDirty();

        Style color = deityStyle(deity);
        DevotionRank rank = DevotionRank.INITIATE;
        player.sendSystemMessage(Component.translatable("bloodmoon.devotion.accept.1", deity.displayName()).withStyle(color.withBold(true)));
        player.sendSystemMessage(Component.translatable("bloodmoon.devotion.accept.2", rank.displayName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        player.sendSystemMessage(Component.translatable("bloodmoon.devotion.accept.3").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        title(player, rank.displayName().copy().withStyle(color),
                Component.translatable("bloodmoon.devotion.accept.sub", deity.inSentence()).withStyle(ChatFormatting.GRAY));
        player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 0.6F);
        player.playNotifySound(SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1F, 0.5F);
        sync(player);
        return 1;
    }

    private static int decline(ServerPlayer player, Deity deity) {
        DevotionData data = DevotionData.get(player.server.overworld());
        DevotionData.Entry e = data.entry(player.getUUID());
        if (e.pendingOffer != deity) {
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.nooffer").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        e.pendingOffer = null;
        data.setDirty();
        player.sendSystemMessage(Component.translatable("bloodmoon.devotion.decline.1", deity.inSentence()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        player.sendSystemMessage(Component.translatable("bloodmoon.devotion.decline.2").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        player.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1F, 0.5F);
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1F, 0.6F);
        return 1;
    }

    private static Style deityStyle(Deity deity) {
        return Style.EMPTY.withColor(TextColor.fromRgb(deity.eyeColor & 0xFFFFFF));
    }

    private static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ---------------------------------------------------------------- comandos

    /** /bloodmoon_devotion accept|decline <deidad>: lo usan los botones del chat (sin permisos). */
    public static void registerPlayerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> accept = Commands.literal("accept");
        LiteralArgumentBuilder<CommandSourceStack> decline = Commands.literal("decline");
        for (Deity d : Deity.values()) {
            accept.then(Commands.literal(d.id()).executes(ctx -> accept(ctx.getSource().getPlayerOrException(), d)));
            decline.then(Commands.literal(d.id()).executes(ctx -> decline(ctx.getSource().getPlayerOrException(), d)));
        }
        dispatcher.register(Commands.literal(ROOT).then(accept).then(decline));
    }

    /** Para pruebas (OP): /bloodmoon devotion offer | add <n> | reset. */
    public static int debugOffer(ServerPlayer player) {
        offer(player.server.overworld(), player, Deity.HARVEST);
        return 1;
    }

    public static int debugAdd(ServerPlayer player, int amount) {
        DevotionData.Entry e = DevotionData.get(player.server.overworld()).entry(player.getUUID());
        if (e.deity == null) {
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.none").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        addReputation(player.server.overworld(), player.getUUID(), player, amount);
        return 1;
    }

    public static int debugReset(ServerPlayer player) {
        DevotionData data = DevotionData.get(player.server.overworld());
        DevotionData.Entry e = data.entry(player.getUUID());
        e.deity = null;
        e.reputation = 0;
        e.pendingOffer = null;
        data.setDirty();
        sync(player);
        return 1;
    }

    // ---------------------------------------------------------------- sincronización

    public static void sync(ServerPlayer player) {
        DevotionData.Entry e = DevotionData.get(player.server.overworld()).entry(player.getUUID());
        PacketDistributor.sendToPlayer(player, new DevotionPayload(e.deity == null ? "" : e.deity.id(), e.reputation));
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
