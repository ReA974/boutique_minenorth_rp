package com.minenorth_shops;

import com.minenorth_eurobank.Money;
import com.minenorth_shops.items.ShopLinkerItem;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
 */
public final class ShopCommands {
    private ShopCommands() {}

    private static final SuggestionProvider<CommandSourceStack> SHOP_IDS = (ctx, b) ->
            SharedSuggestionProvider.suggest(ShopData.get(ctx.getSource().getServer()).ids().stream().map(String::valueOf), b);

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
                                .executes(ShopCommands::linker))));
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
            c.getSource().sendSuccess(() -> Component.literal("Aucune boutique. Créez-en une avec /shops."), false);
            return 0;
        }
        StringBuilder b = new StringBuilder("Boutiques :");
        for (Shop s : all) {
            b.append("\n #").append(s.id).append(" ").append(s.name).append(" — ").append(s.mode.label)
                    .append(", ").append(s.entries.size()).append(" article(s)");
            if (!s.allowCash) b.append(", sans espèces");
            if (!s.allowCard) b.append(", sans carte");
        }
        String txt = b.toString();
        c.getSource().sendSuccess(() -> Component.literal(txt), false);
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
        c.getSource().sendSuccess(() -> Component.literal("« " + s.name + " » assignée à " + count + " entité(s)."), true);
        return n;
    }

    private static int unassign(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int n = 0;
        for (Entity e : EntityArgument.getEntities(c, "targets")) if (EntityLinks.unlink(e)) n++;
        int count = n;
        c.getSource().sendSuccess(() -> Component.literal("Boutique retirée de " + count + " entité(s)."), true);
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
        c.getSource().sendSuccess(() -> Component.literal(txt), false);
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

    private static int linker(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Shop s = shop(c);
        if (s == null) {
            c.getSource().sendFailure(Component.literal("Boutique introuvable."));
            return 0;
        }
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack tool = ShopLinkerItem.create(s);
        if (!p.getInventory().add(tool)) p.drop(tool, false);
        c.getSource().sendSuccess(() -> Component.literal("Outil de liaison pour « " + s.name + " » donné."), false);
        return 1;
    }
}
