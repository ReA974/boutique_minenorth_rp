package com.minenorth_shops;

import com.minenorth_shops.shop.Shop;
import fr.minenorth.api.MineNorth;
import fr.minenorth.api.SecoursService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Boutiques réservées aux pompiers / secours : effectifs du mod Secours lus via MineNorth API.
 * Sécurité : si le service secours est absent, une boutique pompiers est BLOQUÉE pour tout le monde.
 */
public final class PompierCompat {
    private PompierCompat() {}

    /** Le service secours est-il fourni (mod Secours installé) ? */
    public static boolean available() { return MineNorth.secours() != SecoursService.NONE; }

    /** Message de refus, ou null si le joueur peut commercer ici. */
    public static String denial(ServerPlayer p, Shop shop) {
        if (!shop.pompier) return null;
        if (!available()) return "Boutique indisponible : réservée aux pompiers mais le mod Secours est absent.";
        if (!MineNorth.secours().isSecours(p)) return "Cette boutique est réservée aux pompiers.";
        if (ShopConfig.POMPIER_DUTY.get() && !MineNorth.secours().onDuty(p.server, p.getUUID()))
            return "Prenez votre service (tablette Secours) pour utiliser cette boutique.";
        return null;
    }
}
