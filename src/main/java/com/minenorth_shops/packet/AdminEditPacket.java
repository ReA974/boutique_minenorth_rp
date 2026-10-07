package com.minenorth_shops.packet;

import com.minenorth_eurobank.Money;
import com.minenorth_shops.Network;
import com.minenorth_shops.PermisCompat;
import com.minenorth_shops.PoliceCompat;
import com.minenorth_shops.items.ShopLinkerItem;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopData;
import com.minenorth_shops.shop.ShopEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collections;
import java.util.function.Supplier;

/** Client -> serveur : action de l'interface d'administration des boutiques. Réservé aux ops (permission 2). */
public class AdminEditPacket {
    public enum Op {
        OPEN, CLOSE, CREATE, DELETE, RENAME, TOGGLE_MODE, TOGGLE_CASH, TOGGLE_CARD,
        ADD_ENTRY /* entryId = slot */, UPDATE_ENTRY, REMOVE_ENTRY, MOVE_UP, LINKER, PREVIEW,
        SET_LICENCE /* text = id de licence, vide = aucune */,
        CYCLE_POLICE /* tout le monde -> police (tous grades) -> officier et + -> commissaire -> tout le monde */
    }

    public static final int MAX_QTY = 4096;

    private final Op op;
    private final int shopId;
    private final int entryId;
    private final String text;
    private final long price;
    private final int qty;

    public AdminEditPacket(Op op, int shopId, int entryId, String text, long price, int qty) {
        this.op = op;
        this.shopId = shopId;
        this.entryId = entryId;
        this.text = text == null ? "" : text;
        this.price = price;
        this.qty = qty;
    }

    public static AdminEditPacket of(Op op, int shopId) {
        return new AdminEditPacket(op, shopId, 0, "", 0, 0);
    }

    public static void encode(AdminEditPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.op);
        b.writeInt(m.shopId);
        b.writeVarInt(m.entryId);
        b.writeUtf(m.text, 128);
        b.writeLong(m.price);
        b.writeInt(m.qty);
    }

    public static AdminEditPacket decode(FriendlyByteBuf b) {
        return new AdminEditPacket(b.readEnum(Op.class), b.readInt(), b.readVarInt(), b.readUtf(128), b.readLong(), b.readInt());
    }

    public static void handle(AdminEditPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null) return;
            if (m.op == Op.CLOSE) {
                Network.WATCH_ADMIN.remove(p.getUUID());
                return;
            }
            if (!p.hasPermissions(2)) return;
            if (m.op == Op.OPEN) {
                Network.openAdmin(p, m.shopId, "");
                return;
            }
            if (m.op == Op.PREVIEW) {
                Shop s = ShopData.get(p.server).shop(m.shopId);
                if (s != null) {
                    Network.WATCH_ADMIN.remove(p.getUUID());
                    Network.openShop(p, s, -1);
                }
                return;
            }
            int[] focus = {-1};
            String msg = apply(p, m, focus);
            // focus >= 0 : l'écran bascule sur la boutique créée. Les autres admins sont mis à jour au tick suivant.
            Network.send(p, AdminSyncPacket.compute(p.server, false, focus[0], msg));
        });
        ctx.setPacketHandled(true);
    }

    private static String cleanName(String s) {
        s = s.replaceAll("[\\p{Cntrl}§]", "").trim();
        return s.length() > Shop.MAX_NAME ? s.substring(0, Shop.MAX_NAME) : s;
    }

    /** null si l'id est acceptable, sinon le message d'erreur. Partagé avec la commande /shops licence. */
    public static String checkLicence(String lic) {
        if (!lic.matches("[A-Za-z0-9_.:-]{1,64}")) return "Identifiant de licence invalide.";
        if (!PermisCompat.available()) return "Le mod minenorth_permis n'est pas installé sur le serveur.";
        boolean known = PermisCompat.licences().stream().anyMatch(l -> l.id().equals(lic));
        return known ? null : "Licence inconnue : " + lic + " (voir la config de minenorth_permis).";
    }

    private static String apply(ServerPlayer p, AdminEditPacket m, int[] focus) {
        ShopData d = ShopData.get(p.server);

        if (m.op == Op.CREATE) {
            String name = cleanName(m.text);
            if (name.isEmpty()) return "Donnez un nom à la boutique.";
            Shop s = d.create(name);
            focus[0] = s.id;
            return "Boutique « " + name + " » créée (#" + s.id + ").";
        }

        Shop s = d.shop(m.shopId);
        if (s == null) return "Boutique introuvable.";

        switch (m.op) {
            case DELETE -> {
                d.delete(s.id);
                return "Boutique « " + s.name + " » supprimée. Les entités liées ne l'ouvriront plus.";
            }
            case RENAME -> {
                String name = cleanName(m.text);
                if (name.isEmpty()) return "Nom invalide.";
                s.name = name;
                d.changed();
                return "Boutique renommée en « " + name + " ».";
            }
            case TOGGLE_MODE -> {
                s.mode = s.mode.other();
                d.changed();
                return "Mode : " + s.mode.adminLabel + ".";
            }
            case TOGGLE_CASH -> {
                s.allowCash = !s.allowCash;
                d.changed();
                return "Espèces " + (s.allowCash ? "acceptées." : "refusées.");
            }
            case TOGGLE_CARD -> {
                s.allowCard = !s.allowCard;
                d.changed();
                return "Carte " + (s.allowCard ? "acceptée." : "refusée.");
            }
            case SET_LICENCE -> {
                String lic = m.text.trim();
                if (lic.isEmpty()) {
                    s.licence = "";
                    d.changed();
                    return "Plus aucune licence exigée.";
                }
                String err = checkLicence(lic);
                if (err != null) return err;
                s.licence = lic;
                d.changed();
                return "Licence exigée : " + PermisCompat.name(lic) + ".";
            }
            case CYCLE_POLICE -> {
                s.policeGrade = s.policeGrade < 0 ? 2 : s.policeGrade - 1;
                d.changed();
                if (!s.policeOnly()) return "Boutique ouverte à tout le monde.";
                return "Réservée : " + PoliceCompat.label(s.policeGrade) + "."
                        + (PoliceCompat.available() ? "" : " Attention : mod Police absent, boutique bloquée.");
            }
            case ADD_ENTRY -> {
                if (s.entries.size() >= Shop.MAX_ENTRIES) return "Maximum " + Shop.MAX_ENTRIES + " articles par boutique.";
                if (m.price < 0) return "Prix invalide.";
                if (m.qty < 1 || m.qty > MAX_QTY) return "Quantité invalide (1 à " + MAX_QTY + ").";
                // entryId = emplacement d'inventaire choisi dans l'interface (0-35 = inventaire, 40 = main secondaire).
                // L'objet est lu dans l'inventaire SERVEUR : NBT complet (enchantements, nom, données de mods).
                int slot = m.entryId;
                if (slot < 0 || slot >= p.getInventory().getContainerSize() || (slot >= 36 && slot != 40)) {
                    return "Emplacement invalide.";
                }
                ItemStack item = p.getInventory().getItem(slot);
                if (item.isEmpty()) return "Cet emplacement est vide : choisissez un objet de votre inventaire.";
                ShopEntry e = s.addEntry(item, m.qty, m.price);
                d.changed();
                return "Ajouté : " + m.qty + "x " + e.item.getHoverName().getString() + " à " + Money.format(m.price) + ".";
            }
            case UPDATE_ENTRY -> {
                ShopEntry e = s.entry(m.entryId);
                if (e == null) return "Article introuvable.";
                if (m.price < 0) return "Prix invalide.";
                if (m.qty < 1 || m.qty > MAX_QTY) return "Quantité invalide (1 à " + MAX_QTY + ").";
                e.price = m.price;
                e.quantity = m.qty;
                d.changed();
                return "Article modifié : " + m.qty + "x à " + Money.format(m.price) + ".";
            }
            case REMOVE_ENTRY -> {
                ShopEntry e = s.entry(m.entryId);
                if (e == null) return "Article introuvable.";
                s.entries.remove(e);
                d.changed();
                return "Article retiré.";
            }
            case MOVE_UP -> {
                ShopEntry e = s.entry(m.entryId);
                int i = e == null ? -1 : s.entries.indexOf(e);
                if (i > 0) {
                    Collections.swap(s.entries, i, i - 1);
                    d.changed();
                }
                return "";
            }
            case LINKER -> {
                ItemStack tool = ShopLinkerItem.create(s);
                if (!p.getInventory().add(tool)) p.drop(tool, false);
                return "Outil de liaison pour « " + s.name + " » donné. Clic droit sur une entité.";
            }
            default -> {
                return "";
            }
        }
    }
}
