package com.minenorth_shops;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuration serveur : world/serverconfig/minenorth_shops-server.toml */
public final class ShopConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PROTECT_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue FREEZE_ENTITIES;
    public static final ForgeConfigSpec.DoubleValue MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_LOTS;
    public static final ForgeConfigSpec.BooleanValue POMPIER_DUTY;
    public static final ForgeConfigSpec.BooleanValue ILLEGAL_ENABLED;
    public static final ForgeConfigSpec.IntValue ILLEGAL_MINUTES, ILLEGAL_CHECK_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ILLEGAL_RADIUS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("entites");
        PROTECT_ENTITIES = b.comment("Rendre invulnérable une entité quand on lui assigne une boutique.")
                .define("protegerEntites", true);
        FREEZE_ENTITIES = b.comment("Désactiver l'IA (l'entité ne bouge plus) quand on lui assigne une boutique.")
                .define("figerEntites", true);
        MAX_DISTANCE = b.comment("Distance max (blocs) entre le joueur et le vendeur pour acheter/vendre.")
                .defineInRange("distanceMax", 8.0, 2.0, 64.0);
        b.pop();
        b.push("achats");
        MAX_LOTS = b.comment("Nombre max de lots par transaction.")
                .defineInRange("lotsMax", 64, 1, 4096);
        b.pop();
        b.push("restrictions");
        POMPIER_DUTY = b.comment("Boutiques réservées aux pompiers : exiger en plus d'être en service (tablette Secours).")
                .define("pompierServiceRequis", false);
        b.pop();
        b.comment("Boutiques illégales : 2 entités sont assignées à la boutique, une seule est présente à la fois.",
                "Réglages par boutique : /shops illegal <id> ... (minutes et distance, 0 / -1 = ces valeurs).").push("illegal");
        ILLEGAL_ENABLED = b.comment("false = plus aucune rotation : toutes les entités des boutiques illégales restent présentes.")
                .define("actif", true);
        ILLEGAL_MINUTES = b.comment("Minutes entre deux changements d'emplacement d'une boutique illégale.")
                .defineInRange("intervalleMinutes", 15, 1, 10080);
        ILLEGAL_RADIUS = b.comment("Distance (blocs) : si un joueur est plus près que ça de l'entité visible (ou de l'emplacement",
                        "suivant), le changement est repoussé. 0 = jamais repoussé.")
                .defineInRange("distanceJoueurs", 10.0, 0.0, 256.0);
        ILLEGAL_CHECK_SECONDS = b.comment("Toutes les combien de secondes l'état est vérifié (échéance, joueurs proches, chunks chargés).")
                .defineInRange("verificationSecondes", 1, 1, 60);
        b.pop();
        SPEC = b.build();
    }

    private ShopConfig() {}
}
