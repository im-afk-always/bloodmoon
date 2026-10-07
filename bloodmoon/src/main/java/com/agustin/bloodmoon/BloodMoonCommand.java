package com.agustin.bloodmoon;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /bloodmoon force   -> fuerza la Luna de Sangre en la noche entrante
 * /bloodmoon cancel  -> cancela un forzado pendiente
 * /bloodmoon status  -> estado actual y próxima Luna de Sangre
 * Requiere nivel de permiso 2 (OP / trucos activados).
 */
public final class BloodMoonCommand {
    private BloodMoonCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("bloodmoon")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("force").executes(BloodMoonCommand::force))
                .then(Commands.literal("cancel").executes(BloodMoonCommand::cancel))
                .then(Commands.literal("status").executes(BloodMoonCommand::status)));
    }

    private static int force(CommandContext<CommandSourceStack> ctx) {
        ServerLevel overworld = ctx.getSource().getServer().overworld();
        long target = BloodMoonManager.forceNextNight(overworld);
        long today = overworld.getDayTime() / 24000L;
        String key = target == today ? "bloodmoon.command.force.tonight" : "bloodmoon.command.force.tomorrow";
        ctx.getSource().sendSuccess(() -> Component.translatable(key), true);
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
        Component msg;
        if (data.isActive()) {
            msg = Component.translatable("bloodmoon.command.status.active");
        } else if (data.getForcedNightDay() >= 0) {
            msg = Component.translatable("bloodmoon.command.status.forced");
        } else {
            msg = Component.translatable("bloodmoon.command.status.next",
                    BloodMoonManager.daysUntilScheduled(overworld));
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }
}
