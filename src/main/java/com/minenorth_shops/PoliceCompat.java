package com.minenorth_shops;

import com.minenorth_shops.shop.Shop;
import fr.minenorth.api.MineNorth;
import net.minecraft.server.level.ServerPlayer;

/**
 * Boutiques réservées à la police : effectifs du mod Police lus via MineNorth API.
 * Sécurité : si le service police est absent, une boutique police est BLOQUÉE pour tout le monde.
 */
public final class PoliceCompat {
    /** Index = valeur de Shop.policeGrade (0 = Commissaire, 1 = Officier et +, 2 = tout policier). */
    public static final String[] GRADES = {"Commissaire", "Officier", "Sous-officier"};

    private PoliceCompat() {}

    /** Le service police est-il fourni (mod Police installé) ? */
    public static boolean available() { return !MineNorth.police().grades().isEmpty(); }

    /** Grade du joueur (0 = Commissaire … 2 = Sous-officier), -1 s'il n'est pas policier. */
    public static int grade(ServerPlayer p) {
        return MineNorth.police().grade(p.server, p.getUUID());
    }

    /** Libellé de la restriction : « Police (tous grades) », « Police : Officier et + »… */
    public static String label(int policeGrade) {
        if (policeGrade < 0) return "";
        if (policeGrade >= 2) return "Police (tous grades)";
        if (policeGrade == 0) return "Police : Commissaire";
        return "Police : " + GRADES[policeGrade] + " et +";
    }

    /** Message de refus, ou null si le joueur peut commercer ici. */
    public static String denial(ServerPlayer p, Shop shop) {
        if (!shop.policeOnly()) return null;
        if (!available()) return "Boutique indisponible : réservée à la police mais le mod Police est absent.";
        int g = grade(p);
        if (g < 0) return "Cette boutique est réservée à la police.";
        if (g > shop.policeGrade) return "Grade insuffisant : il faut être " + GRADES[shop.policeGrade]
                + (shop.policeGrade == 0 ? "" : " ou plus") + ".";
        return null;
    }
}
