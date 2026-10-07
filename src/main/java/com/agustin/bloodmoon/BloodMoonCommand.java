package com.agustin.bloodmoon;

import com.agustin.bloodmoon.entity.ApocalypseRider;
import com.agustin.bloodmoon.entity.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /bloodmoon force [blood|super|golden|moonless] -> fuerza esa luna en la noche entrante (por defecto: blood)
 * /bloodmoon cancel                      -> cancela un forzado pendiente
 * /bloodmoon status                      -> estado y próximas lunas
 * /bloodmoon summon rider                -> invoca un Jinete del Apocalipsis montado donde estás
 * /bloodmoon summon dragon               -> el Dragón de la Primera Alma desciende sobre vos
 * /bloodmoon summon emissary|executioner -> invoca un caballero del Vacío donde estás (no se retira al amanecer)
 * Requiere permiso 2 (OP / trucos activados).
 */
public final class BloodMoonCommand {
    private BloodMoonCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> force = Commands.literal("force")
                .executes(ctx -> force(ctx, MoonType.BLOOD));
        for (MoonType type : new MoonType[]{MoonType.BLOOD, MoonType.SUPER, MoonType.GOLDEN, MoonType.MOONLESS}) {
            force.then(Commands.literal(type.key()).executes(ctx -> force(ctx, type)));
        }

        dispatcher.register(Commands.literal("bloodmoon")
                .requires(src -> src.hasPermission(2))
                .then(force)
                .then(Commands.literal("cancel").executes(BloodMoonCommand::cancel))
                .then(Commands.literal("status").executes(BloodMoonCommand::status))
                .then(Commands.literal("summon")
                        .then(Commands.literal("rider").executes(BloodMoonCommand::summonRider))
                        .then(Commands.literal("emissary").executes(ctx -> summonKnight(ctx, false)))
                        .then(Commands.literal("executioner").executes(ctx -> summonKnight(ctx, true)))
                        .then(Commands.literal("dragon").executes(BloodMoonCommand::summonDragon))));
    }

    private static Component moonName(MoonType type) {
        return Component.translatable("bloodmoon.moon." + type.key());
    }

    private static int force(CommandContext<CommandSourceStack> ctx, MoonType type) {
        boolean tonight = BloodMoonManager.forceNextNight(ctx.getSource().getServer().overworld(), type);
        String key = tonight ? "bloodmoon.command.force.tonight" : "bloodmoon.command.force.tomorrow";
        ctx.getSource().sendSuccess(() -> Component.translatable(key, moonName(type)), true);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) {
        boolean had = BloodMoonManager.cancelForce(ctx.getSource().getServer().overworld());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                had ? "bloodmoon.command.cancel.ok" : "bloodmoon.command.cancel.none"), true);
        return had ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel overworld = ctx.getSource().getServer().overworld();
        BloodMoonData data = BloodMoonData.get(overworld);
        CommandSourceStack src = ctx.getSource();

        if (data.getActive() != MoonType.NONE) {
            src.sendSuccess(() -> Component.translatable("bloodmoon.command.status.active", moonName(data.getActive())), false);
        }
        if (data.getForcedNightDay() >= 0) {
            src.sendSuccess(() -> Component.translatable("bloodmoon.command.status.forced", moonName(data.getForcedType())), false);
        }
        src.sendSuccess(() -> Component.translatable("bloodmoon.command.status.next",
                days(overworld, MoonType.BLOOD), days(overworld, MoonType.SUPER), days(overworld, MoonType.GOLDEN),
                days(overworld, MoonType.MOONLESS)), false);
        return 1;
    }

    private static String days(ServerLevel overworld, MoonType type) {
        long d = BloodMoonManager.daysUntil(overworld, type);
        return d < 0 ? "-" : Long.toString(d);
    }

    private static int summonKnight(CommandContext<CommandSourceStack> ctx, boolean executioner) {
        CommandSourceStack src = ctx.getSource();
        boolean ok = BloodMoonManager.spawnVoidKnight(src.getLevel(), BlockPos.containing(src.getPosition()),
                executioner ? ModEntities.EXECUTIONER.get() : ModEntities.UNKNOWN_EMISSARY.get(), false) != null;
        src.sendSuccess(() -> Component.translatable(ok ? "bloodmoon.command.summon.knight" : "bloodmoon.command.summon.fail"), true);
        return ok ? 1 : 0;
    }

    private static int summonDragon(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean ok = BloodMoonManager.spawnSoulDragon(src.getLevel(), src.getPosition(), false) != null;
        src.sendSuccess(() -> Component.translatable(ok ? "bloodmoon.command.summon.dragon" : "bloodmoon.command.summon.fail"), true);
        return ok ? 1 : 0;
    }

    private static int summonRider(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean ok = ApocalypseRider.spawnWithMount(src.getLevel(), BlockPos.containing(src.getPosition()));
        src.sendSuccess(() -> Component.translatable(ok ? "bloodmoon.command.summon.ok" : "bloodmoon.command.summon.fail"), true);
        return ok ? 1 : 0;
    }
}
