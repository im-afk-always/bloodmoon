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
        // los aportes quedan hasta la próxima luna (los muestra /bloodmoon devotion status); se limpian en onOfferingBegin
    }

    // ---------------------------------------------------------------- reputación y rangos

    /** Criar animales: reputación para los devotos de la Providencia. */
    public static void onBreed(ServerLevel overworld, ServerPlayer player, int amount) {
        Deity d = DevotionData.get(overworld).entry(player.getUUID()).deity;
        if (d == Deity.PROVIDENCE) {
            addReputation(overworld, player.getUUID(), player, amount, true);
        } else {
            // sin esto, criar sin ser devoto no da ninguna señal y parece que no funciona
            player.displayClientMessage(Component.translatable(d == null ? "bloodmoon.providence.breed.none" : "bloodmoon.providence.breed.other",
                    Deity.PROVIDENCE.inSentence()).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
        }
    }

    /** Kill por encima de la cuota de la noche: +1 de reputación (aviso discreto en la barra de acción). */
    public static void onSurplusKill(ServerLevel overworld, ServerPlayer killer, Deity deity) {
        if (DevotionData.get(overworld).entry(killer.getUUID()).deity == deity) {
            addReputation(overworld, killer.getUUID(), killer, 1, true);
        }
    }

    public static void addReputation(ServerLevel overworld, UUID id, ServerPlayer player, int amount) {
        addReputation(overworld, id, player, amount, false);
    }

    public static void addReputation(ServerLevel overworld, UUID id, ServerPlayer player, int amount, boolean quiet) {
        DevotionData data = DevotionData.get(overworld);
        DevotionData.Entry e = data.entry(id);
        if (e.deity == null || amount == 0) return;
        DevotionRank before = DevotionRank.of(e.reputation);
        e.reputation = Math.max(0, e.reputation + amount);
        data.setDirty();
        DevotionRank after = DevotionRank.of(e.reputation);
        if (after != before) broadcastAuras(overworld.getServer());
        if (player == null) return;
        if (after != before) refreshName(player);
        Style color = deityStyle(e.deity);
        Component gain = Component.translatable(amount > 0 ? "bloodmoon.devotion.gain" : "bloodmoon.devotion.loss",
                Math.abs(amount), e.deity.inSentence(), e.reputation);
        if (quiet) {
            player.displayClientMessage(gain.copy().withStyle(color), true);
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35F, 1.3F + player.getRandom().nextFloat() * 0.3F);
        }
        else player.sendSystemMessage(gain.copy().withStyle(ChatFormatting.GRAY));
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
        broadcastAuras(player.server);
        refreshName(player);
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
        player.sendSystemMessage(Component.translatable("bloodmoon.devotion.decline." + deity.id() + ".1", deity.inSentence()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        player.sendSystemMessage(Component.translatable("bloodmoon.devotion.decline." + deity.id() + ".2").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
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

    /** Para pruebas (OP): /bloodmoon devotion offer [deidad] | add <n> | reset. */
    public static int debugOffer(ServerPlayer player, Deity deity) {
        offer(player.server.overworld(), player, deity);
        return 1;
    }

    /** /bloodmoon devotion status: tu devoción y el pedido en curso (para verificar que todo cuenta). */
    public static int debugStatus(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        DevotionData.Entry e = DevotionData.get(overworld).entry(player.getUUID());
        if (e.deity == null) {
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.none").withStyle(ChatFormatting.GRAY));
        } else {
            DevotionRank rank = DevotionRank.of(e.reputation);
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.status.devotion", e.deity.displayName(), rank.displayName(),
                    rank.roman(), e.reputation).withStyle(deityStyle(e.deity)));
        }
        if (e.pendingOffer != null) {
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.status.pending", e.pendingOffer.displayName()).withStyle(ChatFormatting.GRAY));
        }
        BloodMoonData data = BloodMoonData.get(overworld);
        if (data.isOfferingActive()) {
            Integer mine = DevotionData.get(overworld).contributions().get(player.getUUID());
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.status.request", data.getOfferingDeity().displayName(),
                    data.getOfferingDone(), data.getOfferingRequired(), mine == null ? 0 : mine).withStyle(ChatFormatting.GRAY));
            if (data.isOfferingComplete()) {
                player.sendSystemMessage(Component.translatable("bloodmoon.devotion.status.done").withStyle(ChatFormatting.GRAY));
            }
        } else {
            player.sendSystemMessage(Component.translatable("bloodmoon.devotion.status.norequest").withStyle(ChatFormatting.GRAY));
        }
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
        broadcastAuras(player.server);
        refreshName(player);
        return 1;
    }

    // ---------------------------------------------------------------- sincronización

    public static void sync(ServerPlayer player) {
        DevotionData.Entry e = DevotionData.get(player.server.overworld()).entry(player.getUUID());
        PacketDistributor.sendToPlayer(player, new DevotionPayload(e.deity == null ? "" : e.deity.id(), e.reputation));
    }

    /** Deidad y nivel de cada devoto, a todos los jugadores (para que vean las auras ajenas). */
    public static void broadcastAuras(net.minecraft.server.MinecraftServer server) {
        java.util.List<com.agustin.bloodmoon.network.DevotionAuraPayload.Entry> list = new java.util.ArrayList<>();
        DevotionData.get(server.overworld()).all().forEach((id, e) -> {
            if (e.deity != null) list.add(new com.agustin.bloodmoon.network.DevotionAuraPayload.Entry(id, e.deity.id(), DevotionRank.of(e.reputation).level()));
        });
        PacketDistributor.sendToAllPlayers(new com.agustin.bloodmoon.network.DevotionAuraPayload(list));
    }

    // ---------------------------------------------------------------- prefijo [Rango] en el nombre

    /** "[Apóstol] " con corchetes y rango del color de la facción, o null si no es devoto. */
    public static Component rankPrefix(Deity deity, int level) {
        if (deity == null || level <= 0) return null;
        DevotionRank rank = DevotionRank.values()[Math.min(level, DevotionRank.values().length) - 1];
        return Component.literal("[").append(rank.displayName()).append("]").withStyle(deityStyle(deity))
                .append(Component.literal(" ").withStyle(ChatFormatting.RESET));
    }

    private static Component prefixFor(net.minecraft.world.entity.player.Player player) {
        if (player.level().isClientSide) {
            ClientDevotion.Aura a = ClientDevotion.AURAS.get(player.getUUID());
            return a == null ? null : rankPrefix(a.deity(), a.level());
        }
        if (player.getServer() == null) return null;
        DevotionData.Entry e = DevotionData.get(player.getServer().overworld()).all().get(player.getUUID());
        return e == null || e.deity == null ? null : rankPrefix(e.deity, DevotionRank.of(e.reputation).level());
    }

    /** Nombre en el chat y sobre la cabeza (corre en servidor y cliente). */
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        Component prefix = prefixFor(event.getEntity());
        if (prefix != null) event.setDisplayname(Component.empty().append(prefix).append(event.getDisplayname()));
    }

    /** Nombre en la lista de jugadores (Tab). */
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        Component prefix = prefixFor(event.getEntity());
        if (prefix == null) return;
        Component base = event.getDisplayName() != null ? event.getDisplayName()
                : net.minecraft.world.scores.PlayerTeam.formatNameForTeam(event.getEntity().getTeam(), event.getEntity().getName());
        event.setDisplayName(Component.empty().append(prefix).append(base));
    }

    private static void refreshName(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
            broadcastAuras(player.server);
            refreshName(player);
        }
    }
}
