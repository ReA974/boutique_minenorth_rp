package com.minenorth_shops.shop;

import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.api.BankApi;
import com.minenorth_eurobank.api.PayResult;
import com.minenorth_shops.PermisCompat;
import com.minenorth_shops.ShopConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Logique des transactions, exécutée côté serveur uniquement. */
public final class ShopService {
    public enum Method { CASH, CARD }

    private ShopService() {}

    /** Emplacements pris en compte : inventaire principal + main secondaire (pas l'armure). */
    private static boolean usableSlot(int i) {
        return i < 36 || i == 40;
    }

    public static int countMatching(ServerPlayer p, ShopEntry e) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!usableSlot(i)) continue;
            ItemStack s = inv.getItem(i);
            if (e.matches(s)) n += s.getCount();
        }
        return n;
    }

    private static void removeMatching(ServerPlayer p, ShopEntry e, int amount) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            if (!usableSlot(i)) continue;
            ItemStack s = inv.getItem(i);
            if (!e.matches(s)) continue;
            int take = Math.min(amount, s.getCount());
            s.shrink(take);
            amount -= take;
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        inv.setChanged();
    }

    private static void giveItems(ServerPlayer p, ShopEntry e, int amount) {
        int max = Math.max(1, e.item.getMaxStackSize());
        while (amount > 0) {
            int n = Math.min(amount, max);
            Money.giveStack(p, e.item.copyWithCount(n));
            amount -= n;
        }
        p.containerMenu.broadcastChanges();
    }

    /** Nombre de lots maximum faisable maintenant (pour le bouton « Max »). */
    public static int maxLots(ServerPlayer p, Shop shop, ShopEntry e) {
        int cap = ShopConfig.MAX_LOTS.get();
        if (shop.mode == ShopMode.BUY) return Math.min(cap, countMatching(p, e) / Math.max(1, e.quantity));
        if (e.price <= 0) return cap;
        long money = Math.max(shop.allowCash ? Money.cashIn(p) : 0, shop.allowCard ? BankApi.balance(p) : 0);
        return (int) Math.min(cap, money / e.price);
    }

    /**
     * Effectue l'achat (boutique en VENTE) ou la vente (boutique en RACHAT).
     * @return message à afficher au joueur
     */
    public static String transact(ServerPlayer p, Shop shop, ShopEntry e, int lots, Method method) {
        String denied = PermisCompat.denial(p, shop.licence);
        if (denied != null) return denied;
        if (lots == -1) lots = maxLots(p, shop, e);
        if (lots <= 0) return shop.mode == ShopMode.BUY ? "Vous n'avez pas assez de cet objet." : "Fonds insuffisants.";
        lots = Math.min(lots, ShopConfig.MAX_LOTS.get());
        if (method == Method.CASH && !shop.allowCash) return "Cette boutique n'accepte pas les espèces.";
        if (method == Method.CARD && !shop.allowCard) return "Cette boutique n'accepte pas la carte.";

        long total;
        int items;
        try {
            total = Math.multiplyExact(e.price, (long) lots);
            items = Math.multiplyExact(e.quantity, lots);
        } catch (ArithmeticException ex) {
            return "Quantité trop grande.";
        }
        String what = items + "x " + e.item.getHoverName().getString();

        if (shop.mode == ShopMode.SELL) {
            if (method == Method.CASH) {
                long cash = Money.cashIn(p);
                if (cash < total) return "Espèces insuffisantes (" + Money.format(cash) + " sur vous).";
                if (total > 0) {
                    long taken = Money.takeAllCash(p);
                    Money.giveCash(p, taken - total);   // rendu de monnaie
                }
            } else if (total > 0) {
                PayResult r = BankApi.charge(p, total);
                if (r != PayResult.OK) return r.message();
            }
            giveItems(p, e, items);
            return "Achat : " + what + " pour " + Money.format(total) + ".";
        }

        // boutique en RACHAT : le joueur vend
        if (countMatching(p, e) < items) return "Il vous faut " + what + ".";
        if (method == Method.CARD && !BankApi.hasAccount(p)) return PayResult.NO_ACCOUNT.message();
        removeMatching(p, e, items);
        if (total > 0) {
            if (method == Method.CASH) Money.giveCash(p, total);
            else BankApi.refund(p, total);   // crédite le compte
        }
        p.containerMenu.broadcastChanges();
        return "Vente : " + what + " pour " + Money.format(total)
                + (method == Method.CARD ? " (versé sur votre compte)." : ".");
    }
}
