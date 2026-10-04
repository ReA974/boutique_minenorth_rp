package com.minenorth_shops;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuration serveur : world/serverconfig/minenorth_shops-server.toml */
public final class ShopConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PROTECT_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue FREEZE_ENTITIES;
    public static final ForgeConfigSpec.DoubleValue MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_LOTS;

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
        SPEC = b.build();
    }

    private ShopConfig() {}
}
