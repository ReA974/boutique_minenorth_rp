package com.minenorth_shops;

import com.minenorth_eurobank.Money;
import com.minenorth_shops.items.ShopLinkerItem;
import com.minenorth_shops.packet.AdminEditPacket;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

/**
 * /shops                               ouvre l'interface d'administration
 * /shops list                          liste les boutiques
 * /shops edit <id>                     ouvre l'édition d'une boutique
 * /shops assign <entités> <id>         assigne une boutique (ex. @e[type=villager,limit=1,sort=nearest])
 * /shops unassign <entités>            retire la boutique
 * /shops info <entité>                 quelle boutique est liée à cette entité
 * /shops open <id> [joueurs]           ouvre la boutique pour des joueurs (blocs de commande, PNJ d'autres mods...)
 * /shops linker <id>                   donne l'outil de liaison
 * /shops licence <id> <licence|aucune> licence (mod minenorth_permis) exigée pour commercer
 * /shops police <id> <tous|policier|officier|commissaire> boutique réservée à la police (grade minimum)
 * /shops pompier <id> <on|off>         boutique réservée aux pompiers / secours
 * /shops illegal <id> <on|off> [minutes] [distance]  boutique illégale : 2 entités, une seule présente à la fois
 * /shops illegal <id> statut | basculer
 */
public final class ShopCommands {
    private ShopCommands() {}

    private static final SuggestionProvider<CommandSourceStack> SHOP_IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(ShopData.get(ctx.getSource().getServer()).ids().stream().map(String::valueOf), b);

    private static final SuggestionProvider<CommandSourceStack> LICENCES = (ctx, b) -> {
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add("aucune");
        for (PermisCompat.LicenceInfo l : PermisCompat.licences()) ids.add(l.id());
        return SharedSuggestionProvider.suggest(ids, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("shops").requires(s -> s.hasPermission(2))
                .executes(c -> openAdmin(c, -1))
                .then(Commands.literal("list").executes(ShopCommands::list))
                .then(Commands.literal("edit")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(SHOP_IDS)
                                .executes(c -> openAdmin(c, IntegerArgumentType.getInteger(c, "id")))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(SHOP_IDS)
                                        .executes(ShopCommands::assign))))
                .then(Commands.literal("unassign")
                        .then(Commands.argument("targets", EntityArgument.entities()).executes(ShopCommands::unassign)))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", EntityArgument.entity()).executes(ShopCommands::info)))
                .then(Commands.literal("open")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(SHOP_IDS)
                                .executes(c -> open(c, java.util.List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(c -> open(c, EntityArgument.getPlayers(c, "players"))))))
                .then(Commands.literal("linker")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(SHOP_IDS)
                                .executes(ShopCommands::linker)))
                .then(Commands.literal("licence")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(SHOP_IDS)
                                .then(Commands.argument("licence", StringArgumentType.string()).suggests(LICENCES)
                                        .executes(ShopCommands::licence))))
                .then(Commands.literal("police")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(SHOP_IDS)
                                .then(Commands.argument("acces", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                java.util.List.of("tous", "policier", "officier", "commissaire"), b))
                                        .executes(ShopCommands::police))))
                .then(Commands.literal("pompier")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(SHOP_IDS)
                                .then(Commands.argument("etat", StringArgumentType.word()).suggests(ON_OFF)
                                        .executes(ShopCommands::pompier))))
                .then(Commands.literal("illegal")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(SHOP_IDS)
                                .then(Commands.literal("statut").executes(ShopCommands::illegalStatus))
                                .then(Commands.literal("basculer").executes(ShopCommands::illegalSwap))
                                .then(Commands.argument("etat", StringArgumentType.word()).suggests(ON_OFF)
                                        .executes(c -> illegal(c, 0, -1, false))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 10080))
                                                .executes(c -> illegal(c, IntegerArgumentType.getInteger(c, "minutes"), -1, false))
                                                .then(Commands.argument("distance", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-1, 256))
                                                        .executes(c -> illegal(c, IntegerArgumentType.getInteger(c, "minutes"),
                                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "distance"), true))))))));
    }

    private static final SuggestionProvider<CommandSourceStack> ON_OFF = (ctx, b) ->
            SharedSuggestionProvider.suggest(java.util.List.of("on", "off"), b);

    private static Boolean parseOnOff(String s) {
        return switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "on", "oui", "true", "1" -> Boolean.TRUE;
            case "off", "non", "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static int pompier(CommandContext<CommandSourceStack> c) {
        Shop s = shop(c);
        Boolean on = parseOnOff(StringArgumentType.getString(c, "etat"));
        if (s == null || on == null) {
            c.getSource().sendFailure(Component.literal(s == null ? "Boutique introuvable." : "État : on ou off."));
            return 0;
        }
        s.pompier = on;
        if (on) s.policeGrade = -1;
        ShopData.get(c.getSource().getServer()).changed();
        c.getSource().sendSystemMessage(Component.literal(on
                ? "« " + s.name + " » est réservée aux pompiers / secours." + (PompierCompat.available() ? "" : " (mod Secours absent : boutique bloquée)")
                : "« " + s.name + " » n'est plus réservée aux pompiers."));
        return 1;
    }

    /** minutes 0 = valeur de la config ; distance : seulement si donnée (-1 = valeur de la config, 0 = jamais repoussé). */
    private static int illegal(CommandContext<CommandSourceStack> c, int minutes, double distance, boolean setDistance) {
        Shop s = shop(c);
        Boolean on = parseOnOff(StringArgumentType.getString(c, "etat"));
        if (s == null || on == null) {
            c.getSource().sendFailure(Component.literal(s == null ? "Boutique introuvable." : "État : on ou off."));
            return 0;
        }
        s.illegal = on;
        if (on) {
            s.illegalMinutes = minutes;
            if (setDistance) s.illegalRadius = distance;
        }
        ShopData.get(c.getSource().getServer()).changed();
        c.getSource().sendSystemMessage(Component.literal(on
                ? "« " + s.name + " » est illégale : une seule de ses entités est présente à la fois, changement toutes les "
                + (s.illegalMinutes > 0 ? s.illegalMinutes : ShopConfig.ILLEGAL_MINUTES.get()) + " min, repoussé si un joueur est à moins de "
                + (s.illegalRadius >= 0 ? s.illegalRadius : ShopConfig.ILLEGAL_RADIUS.get()) + " blocs."
                : "« " + s.name + " » est légale : toutes ses entités reviennent."));
        return 1;
    }

    private static int illegalStatus(CommandContext<CommandSourceStack> c) {
        Shop s = shop(c);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Boutique introuvable."));
            return 0;
        }
        c.getSource().sendSystemMessage(Component.literal("« " + s.name + " » : " + IllegalRotation.status(c.getSource().getServer(), s)));
        return 1;
    }

    private static int illegalSwap(CommandContext<CommandSourceStack> c) {
        Shop s = shop(c);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Boutique introuvable."));
            return 0;
        }
        if (!IllegalRotation.forceSwap(c.getSource().getServer(), s)) {
            c.getSource().sendFailure(Component.literal("Impossible : boutique non illégale, ou moins de 2 entités assignées."));
            return 0;
        }
        c.getSource().sendSystemMessage(Component.literal("Changement d'emplacement fait."));
        return 1;
    }

    private static Shop shop(CommandContext<CommandSourceStack> c) {
        return ShopData.get(c.getSource().getServer()).shop(IntegerArgumentType.getInteger(c, "id"));
    }

    private static int openAdmin(CommandContext<CommandSourceStack> c, int focus) throws CommandSyntaxException {
        Network.openAdmin(c.getSource().getPlayerOrException(), focus, "");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        Collection<Shop> all = ShopData.get(c.getSource().getServer()).all();
        if (all.isEmpty()) {
            c.getSource().sendSystemMessage(Component.literal("Aucune boutique. Créez-en une avec /shops."));
            return 0;
        }
        StringBuilder b = new StringBuilder("Boutiques :");
        for (Shop s : all) {
            b.append("\n #").append(s.id).append(" ").append(s.name).append(" — ").append(s.mode.label)
                    .append(", ").append(s.entries.size()).append(" article(s)");
            if (!s.allowCash) b.append(", sans espèces");
            if (!s.allowCard) b.append(", sans carte");
            if (s.requiresLicence()) b.append(", licence : ").append(PermisCompat.name(s.licence));
            if (s.policeOnly()) b.append(", ").append(PoliceCompat.label(s.policeGrade));
            if (s.pompier) b.append(", pompiers");
            if (s.illegal) b.append(", ILLÉGALE");
        }
        String txt = b.toString();
        c.getSource().sendSystemMessage(Component.literal(txt));
        return all.size();
    }

    private static int assign(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Shop s = shop(c);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Boutique introuvable."));
            return 0;
        }
        int n = 0;
        for (Entity e : EntityArgument.getEntities(c, "targets")) {
            if (e instanceof Player) continue;   // pas sur les joueurs
            EntityLinks.link(e, s.id);
            n++;
        }
        int count = n;
        c.getSource().sendSystemMessage(Component.literal("« " + s.name + " » assignée à " + count + " entité(s)."));
        return n;
    }

    private static int unassign(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int n = 0;
        for (Entity e : EntityArgument.getEntities(c, "targets")) if (EntityLinks.unlink(e)) n++;
        int count = n;
        c.getSource().sendSystemMessage(Component.literal("Boutique retirée de " + count + " entité(s)."));
        return n;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, "target");
        int id = EntityLinks.shopOf(e);
        Shop s = id < 0 ? null : ShopData.get(c.getSource().getServer()).shop(id);
        String txt = id < 0 ? e.getDisplayName().getString() + " n'a pas de boutique."
                : s == null ? e.getDisplayName().getString() + " est liée à #" + id + " (supprimée)."
                : e.getDisplayName().getString() + " → #" + id + " " + s.name + " (" + s.mode.label + ", "
                + s.entries.size() + " articles"
                + (s.entries.isEmpty() ? "" : ", dès " + Money.format(s.entries.stream().mapToLong(x -> x.price).min().orElse(0))) + ")";
        c.getSource().sendSystemMessage(Component.literal(txt));
        return id < 0 ? 0 : 1;
    }

    private static int open(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        Shop s = shop(c);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Boutique introuvable."));
            return 0;
        }
        for (ServerPlayer p : players) Network.openShop(p, s, -1);
        return players.size();
    }

    private static int licence(CommandContext<CommandSourceStack> c) {
        Shop s = shop(c);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Boutique introuvable."));
            return 0;
        }
        String lic = StringArgumentType.getString(c, "licence").trim();
        if (lic.equalsIgnoreCase("aucune") || lic.equalsIgnoreCase("none")) lic = "";
        if (!lic.isEmpty()) {
            String err = AdminEditPacket.checkLicence(lic);
            if (err != null) {
                c.getSource().sendFailure(Component.literal(err));
                return 0;
            }
        }
        s.licence = lic;
        ShopData.get(c.getSource().getServer()).changed();
        String txt = lic.isEmpty() ? "« " + s.name + " » : plus aucune licence exigée."
                : "« " + s.name + " » exige désormais : " + PermisCompat.name(lic) + ".";
        c.getSource().sendSystemMessage(Component.literal(txt));
        return 1;
    }

    private static int police(CommandContext<CommandSourceStack> c) {
        Shop s = shop(c);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Boutique introuvable."));
            return 0;
        }
        String a = StringArgumentType.getString(c, "acces").toLowerCase(java.util.Locale.ROOT);
        int g = switch (a) {
            case "tous", "aucun", "none" -> -1;
            case "policier", "police", "sousofficier", "sous-officier" -> 2;
            case "officier" -> 1;
            case "commissaire" -> 0;
            default -> -2;
        };
        if (g == -2) {
            c.getSource().sendFailure(Component.literal("Accès inconnu : tous, policier, officier ou commissaire."));
            return 0;
        }
        s.policeGrade = g;
        if (g >= 0) s.pompier = false;
        ShopData.get(c.getSource().getServer()).changed();
        String txt = g < 0 ? "« " + s.name + " » est ouverte à tout le monde."
                : "« " + s.name + " » est réservée : " + PoliceCompat.label(g) + "."
                + (PoliceCompat.available() ? "" : " (mod Police absent : boutique bloquée)");
        c.getSource().sendSystemMessage(Component.literal(txt));
        return 1;
    }

    private static int linker(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Shop s = shop(c);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Boutique introuvable."));
            return 0;
        }
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack tool = ShopLinkerItem.create(s);
        if (!p.getInventory().add(tool)) p.drop(tool, false);
        c.getSource().sendSystemMessage(Component.literal("Outil de liaison pour « " + s.name + " » donné."));
        return 1;
    }
}
