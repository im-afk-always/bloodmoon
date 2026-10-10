package com.agustin.bloodmoon;

import com.agustin.bloodmoon.entity.ApocalypseRider;
import com.agustin.bloodmoon.entity.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /bloodmoon force [super|golden|moonless] -> fuerza esa luna en la noche entrante (por defecto: super)
 * /bloodmoon cancel                      -> cancela un forzado pendiente
 * /bloodmoon status                      -> estado y próximas lunas
 * /bloodmoon summon rider                -> invoca un Jinete del Apocalipsis montado donde estás
 * /bloodmoon summon dragon               -> el Dragón de la Primera Alma desciende sobre vos
 * /bloodmoon summon emissary|executioner -> invoca un caballero del Vacío donde estás (no se retira al amanecer)
 * /bloodmoon locate coliseum             -> Coliseo del Vacío más cercano (clic para teletransportarte)
 * /bloodmoon summon eye                  -> el Observador despierta sobre vos (su estrado = donde estás parado)
 * /bloodmoon summon unbound              -> el Observador Desatado emerge donde estás (prueba de la fase final)
 * /bloodmoon locate sanctum              -> Santuario del Ojo más cercano (en el Laberinto del Vacío)
 * /bloodmoon eclipse [cancel]            -> Eclipse Solar ahora (lleva la hora al amanecer) / cancelarlo
 * /bloodmoon invasion start|grow <ciclos>|end|status|raid -> Invasión del Vacío (pruebas)
 * /bloodmoon devotion offer [harvest|providence]|add <n>|reset|status|complete -> pruebas del culto: propuesta, sumar reputación, borrar devoción
 * /bloodmoon eclipse permanent           -> congela el eclipse en el instante actual (otra vez: sigue su curso)
 * Requiere permiso 2 (OP / trucos activados).
 */
public final class BloodMoonCommand {
    private BloodMoonCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        DevotionManager.registerPlayerCommand(dispatcher);

        LiteralArgumentBuilder<CommandSourceStack> force = Commands.literal("force")
                .executes(ctx -> force(ctx, MoonType.SUPER));
        for (MoonType type : new MoonType[]{MoonType.SUPER, MoonType.GOLDEN, MoonType.MOONLESS}) {
            force.then(Commands.literal(type.key()).executes(ctx -> force(ctx, type)));
        }

        dispatcher.register(Commands.literal("bloodmoon")
                .requires(src -> src.hasPermission(2))
                .then(force)
                .then(Commands.literal("cancel").executes(BloodMoonCommand::cancel))
                .then(Commands.literal("invasion")
                        .then(Commands.literal("start").executes(BloodMoonCommand::invasionStart))
                        .then(Commands.literal("grow").then(Commands.argument("cycles", IntegerArgumentType.integer(1, 5000))
                                .executes(ctx -> invasionGrow(ctx, IntegerArgumentType.getInteger(ctx, "cycles")))))
                        .then(Commands.literal("end").executes(BloodMoonCommand::invasionEnd))
                        .then(Commands.literal("build").executes(ctx -> com.agustin.bloodmoon.invasion.ConstructionSites.forceHere(
                                ctx.getSource().getServer().overworld(), ctx.getSource().getPlayerOrException()) ? 1 : 0)
                                .then(Commands.literal("nest").executes(ctx -> buildHere(ctx, com.agustin.bloodmoon.invasion.DominionStructures.NEST)))
                                .then(Commands.literal("tower").executes(ctx -> buildHere(ctx, com.agustin.bloodmoon.invasion.DominionStructures.TOWER)))
                                .then(Commands.literal("fortress").executes(ctx -> buildHere(ctx, com.agustin.bloodmoon.invasion.DominionStructures.FORTRESS))))
                        .then(Commands.literal("raid").executes(ctx -> com.agustin.bloodmoon.invasion.InvasionRaids.force(
                                ctx.getSource().getServer().overworld(), ctx.getSource().getPlayerOrException()) ? 1 : 0)
                                .then(Commands.literal("stop").executes(ctx -> com.agustin.bloodmoon.invasion.InvasionRaids.stop(
                                        ctx.getSource().getServer().overworld(), ctx.getSource().getPlayerOrException()) ? 1 : 0))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 10)).executes(ctx -> {
                                    boolean ok = com.agustin.bloodmoon.invasion.InvasionRaids.force(ctx.getSource().getServer().overworld(),
                                            ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "tier"));
                                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Asalto iniciado"
                                            : "No se pudo: fuera de un Dominio activo, ya hay un asalto o no hay lugar para la puerta"), false);
                                    return ok ? 1 : 0;
                                })))
                        .then(Commands.literal("status").executes(ctx -> {
                            String st = com.agustin.bloodmoon.invasion.InvasionManager.status(ctx.getSource().getServer().overworld());
                            ctx.getSource().sendSuccess(() -> Component.literal(st), false);
                            return 1;
                        })))
                .then(Commands.literal("devotion")
                        .then(devotionOffer())
                        .then(Commands.literal("status").executes(ctx -> DevotionManager.debugStatus(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("complete").executes(ctx -> OfferingManager.debugComplete(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer(-100000, 100000))
                                .executes(ctx -> DevotionManager.debugAdd(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("reset").executes(ctx -> DevotionManager.debugReset(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("eclipse")
                        .executes(ctx -> {
                            EclipseManager.startNow(ctx.getSource().getServer().overworld());
                            ctx.getSource().sendSuccess(() -> Component.translatable("bloodmoon.command.eclipse.start"), true);
                            return 1;
                        })
                        .then(Commands.literal("permanent").executes(ctx -> {
                            EclipseManager.PermanentResult r = EclipseManager.togglePermanent(ctx.getSource().getServer().overworld());
                            String key = switch (r) {
                                case STARTED -> "bloodmoon.command.eclipse.permanent.started";
                                case FROZEN -> "bloodmoon.command.eclipse.permanent.on";
                                case RELEASED -> "bloodmoon.command.eclipse.permanent.off";
                            };
                            ctx.getSource().sendSuccess(() -> Component.translatable(key), true);
                            return 1;
                        }))
                        .then(Commands.literal("cancel").executes(ctx -> {
                            boolean had = EclipseManager.cancel(ctx.getSource().getServer().overworld());
                            ctx.getSource().sendSuccess(() -> Component.translatable(had ? "bloodmoon.command.eclipse.cancel" : "bloodmoon.command.eclipse.none"), true);
                            return had ? 1 : 0;
                        })))
                .then(Commands.literal("status").executes(BloodMoonCommand::status))
                .then(Commands.literal("summon")
                        .then(Commands.literal("rider").executes(BloodMoonCommand::summonRider))
                        .then(Commands.literal("emissary").executes(ctx -> summonKnight(ctx, false)))
                        .then(Commands.literal("executioner").executes(ctx -> summonKnight(ctx, true)))
                        .then(Commands.literal("dragon").executes(BloodMoonCommand::summonDragon))
                        .then(Commands.literal("eye").executes(BloodMoonCommand::summonEye))
                        .then(Commands.literal("unbound").executes(BloodMoonCommand::summonUnbound))
                        .then(Commands.literal("palm").executes(ctx -> {
                            var src = ctx.getSource();
                            boolean ok = com.agustin.bloodmoon.entity.VoidPalm.summon(src.getLevel(), null, src.getPosition()) != null;
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("intro").executes(ctx -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(ctx.getSource().getPlayerOrException(),
                            new com.agustin.bloodmoon.network.IntroPayload());
                    return 1;
                }))
                .then(Commands.literal("locate")
                        .then(Commands.literal("coliseum").executes(BloodMoonCommand::locateColiseum))
                        .then(Commands.literal("sanctum").executes(BloodMoonCommand::locateSanctum))));
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

    private static LiteralArgumentBuilder<CommandSourceStack> devotionOffer() {
        LiteralArgumentBuilder<CommandSourceStack> offer = Commands.literal("offer")
                .executes(ctx -> DevotionManager.debugOffer(ctx.getSource().getPlayerOrException(), Deity.HARVEST));
        for (Deity d : Deity.values()) {
            offer.then(Commands.literal(d.id()).executes(ctx -> DevotionManager.debugOffer(ctx.getSource().getPlayerOrException(), d)));
        }
        return offer;
    }

    /** Despierta el coliseo más cercano (como si se encendiera su portal). */
    private static int invasionStart(CommandContext<CommandSourceStack> ctx) {
        ServerLevel ow = ctx.getSource().getServer().overworld();
        net.minecraft.core.BlockPos from = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition());
        var site = com.agustin.bloodmoon.world.ColiseumSites.nearest(ow, from, 2);
        if (site.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("bloodmoon.command.locate.none"));
            return 0;
        }
        var f = com.agustin.bloodmoon.invasion.InvasionManager.awaken(ow, site.get().center());
        ctx.getSource().sendSuccess(() -> Component.literal("Dominio " + f.name + " @ " + f.center.toShortString()), true);
        return 1;
    }

    private static int invasionGrow(CommandContext<CommandSourceStack> ctx, int cycles) {
        ServerLevel ow = ctx.getSource().getServer().overworld();
        for (int i = 0; i < cycles; i++) com.agustin.bloodmoon.invasion.InvasionManager.runCycle(ow);
        String st = com.agustin.bloodmoon.invasion.InvasionManager.status(ow);
        ctx.getSource().sendSuccess(() -> Component.literal(st), true);
        return 1;
    }

    private static int invasionEnd(CommandContext<CommandSourceStack> ctx) {
        ServerLevel ow = ctx.getSource().getServer().overworld();
        var f = com.agustin.bloodmoon.invasion.InvasionManager.nearest(ow, net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
        if (f == null || !f.active) {
            ctx.getSource().sendFailure(Component.literal("-"));
            return 0;
        }
        com.agustin.bloodmoon.invasion.InvasionManager.defeat(ow, com.agustin.bloodmoon.invasion.InvasionData.get(ow), f);
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
                days(overworld, MoonType.SUPER), days(overworld, MoonType.GOLDEN),
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

    private static int locateColiseum(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        net.minecraft.server.level.ServerLevel overworld = src.getServer().overworld();
        net.minecraft.core.BlockPos from = net.minecraft.core.BlockPos.containing(src.getPosition());
        var site = com.agustin.bloodmoon.world.ColiseumSites.nearest(overworld, from, 8);
        if (site.isEmpty()) {
            src.sendFailure(Component.translatable("bloodmoon.command.locate.none"));
            return 0;
        }
        net.minecraft.core.BlockPos c = site.get().center();
        int dist = (int) Math.sqrt(from.distSqr(new net.minecraft.core.BlockPos(c.getX(), from.getY(), c.getZ())));
        Component coords = net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets(
                Component.literal(c.getX() + ", " + (c.getY() + 1) + ", " + c.getZ())).withStyle(style -> style
                .withColor(net.minecraft.ChatFormatting.GREEN)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                        "/execute in minecraft:overworld run tp @s " + c.getX() + " " + (c.getY() + 1) + " " + (c.getZ() - 120)))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("chat.coordinates.tooltip"))));
        src.sendSuccess(() -> Component.translatable("bloodmoon.command.locate.found", coords, dist), false);
        return 1;
    }

    private static int summonDragon(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean ok = BloodMoonManager.spawnSoulDragon(src.getLevel(), src.getPosition(), false) != null;
        src.sendSuccess(() -> Component.translatable(ok ? "bloodmoon.command.summon.dragon" : "bloodmoon.command.summon.fail"), true);
        return ok ? 1 : 0;
    }

    private static int summonEye(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean ok = com.agustin.bloodmoon.world.EyeSanctums.spawn(src.getLevel(), src.getPosition()) != null;
        src.sendSuccess(() -> Component.translatable(ok ? "bloodmoon.command.summon.eye" : "bloodmoon.command.summon.fail"), true);
        return ok ? 1 : 0;
    }

    private static int summonUnbound(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var boss = ModEntities.UNBOUND_OBSERVER.get().create(src.getLevel());
        boolean ok = boss != null;
        if (ok) {
            var p = src.getPosition();
            boss.moveTo(p.x, p.y + 150, p.z, 0F, 0F);
            boss.setHome(p);
            boss.finalizeSpawn(src.getLevel(), src.getLevel().getCurrentDifficultyAt(BlockPos.containing(p)),
                    net.minecraft.world.entity.MobSpawnType.COMMAND, null);
            src.getLevel().addFreshEntity(boss);
        }
        src.sendSuccess(() -> Component.translatable(ok ? "bloodmoon.command.summon.unbound" : "bloodmoon.command.summon.fail"), true);
        return ok ? 1 : 0;
    }

    private static int locateSanctum(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel lab = src.getServer().getLevel(com.agustin.bloodmoon.registry.ModDimensions.VOID_LABYRINTH);
        if (lab == null) {
            src.sendFailure(Component.translatable("bloodmoon.command.locate.none"));
            return 0;
        }
        BlockPos from = BlockPos.containing(src.getPosition());
        BlockPos c = com.agustin.bloodmoon.world.EyeSanctums.nearest(lab, from);
        if (c == null) {
            src.sendFailure(Component.translatable("bloodmoon.command.locate.none"));
            return 0;
        }
        int dist = (int) Math.sqrt(from.distSqr(new BlockPos(c.getX(), from.getY(), c.getZ())));
        String dim = lab.dimension().location().toString();
        Component coords = net.minecraft.network.chat.ComponentUtils.wrapInSquareBrackets(
                Component.literal(c.getX() + ", " + c.getY() + ", " + c.getZ())).withStyle(style -> style
                .withColor(net.minecraft.ChatFormatting.GREEN)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                        "/execute in " + dim + " run tp @s " + (c.getX() + 94) + " " + c.getY() + " " + c.getZ()))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("chat.coordinates.tooltip"))));
        src.sendSuccess(() -> Component.translatable("bloodmoon.command.locate.sanctum", coords, dist), false);
        return 1;
    }

    private static int summonRider(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean ok = ApocalypseRider.spawnWithMount(src.getLevel(), BlockPos.containing(src.getPosition()));
        src.sendSuccess(() -> Component.translatable(ok ? "bloodmoon.command.summon.ok" : "bloodmoon.command.summon.fail"), true);
        return ok ? 1 : 0;
    }

    /** Para pruebas: levanta una estructura mayor del Dominio (con Forjadores) centrada en tu chunk. */
    private static int buildHere(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, int type)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean ok = com.agustin.bloodmoon.invasion.ConstructionSites.forceStructureHere(ctx.getSource().getServer().overworld(),
                ctx.getSource().getPlayerOrException(), type);
        ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Obra iniciada" : "Fuera de un Dominio activo o ya hay una obra aquí"), false);
        return ok ? 1 : 0;
    }
}
