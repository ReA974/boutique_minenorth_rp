package com.minenorth_shops.shop;

/** Sens d'une boutique. */
public enum ShopMode {
    /** La boutique VEND : le joueur paie et reçoit les objets. */
    SELL("Vente", "Vend aux joueurs"),
    /** La boutique RACHÈTE : le joueur donne ses objets et est payé. */
    BUY("Rachat", "Achète aux joueurs");

    public final String label;
    public final String adminLabel;

    ShopMode(String label, String adminLabel) {
        this.label = label;
        this.adminLabel = adminLabel;
    }

    public ShopMode other() {
        return this == SELL ? BUY : SELL;
    }
}
