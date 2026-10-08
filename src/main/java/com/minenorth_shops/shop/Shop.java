package com.minenorth_shops.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Une boutique. Partagée client/serveur (sérialisation NBT pour la sauvegarde, buffer pour le réseau). */
public class Shop {
    public static final int MAX_NAME = 32;
    public static final int MAX_ENTRIES = 54;

    public final int id;
    public String name;
    public ShopMode mode = ShopMode.SELL;
    public boolean allowCash = true;
    public boolean allowCard = true;
    /** Licence (mod minenorth_permis) exigée pour acheter / vendre ici. Vide = aucune. */
    public String licence = "";
    /** Réservée à la police (mod minenorthpolice) : -1 = tout le monde, sinon grade minimum (0 = Commissaire, 1 = Officier, 2 = tout policier). */
    public int policeGrade = -1;
    public final List<ShopEntry> entries = new ArrayList<>();
    private int nextEntryId = 1;

    public Shop(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public ShopEntry addEntry(net.minecraft.world.item.ItemStack item, int qty, long price) {
        ShopEntry e = new ShopEntry(nextEntryId++, item, qty, price);
        entries.add(e);
        return e;
    }

    /** Rubriques utilisées par les articles, dans l'ordre d'apparition. */
    public List<String> categories() {
        List<String> out = new ArrayList<>();
        for (ShopEntry e : entries) if (!e.category.isEmpty() && !out.contains(e.category)) out.add(e.category);
        return out;
    }

    public boolean policeOnly() {
        return policeGrade >= 0;
    }

    public boolean requiresLicence() {
        return licence != null && !licence.isBlank();
    }

    @Nullable
    public ShopEntry entry(int entryId) {
        for (ShopEntry e : entries) if (e.id == entryId) return e;
        return null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", id);
        t.putString("Name", name);
        t.putString("Mode", mode.name());
        t.putBoolean("Cash", allowCash);
        t.putBoolean("Card", allowCard);
        t.putString("Licence", licence);
        t.putInt("Police", policeGrade);
        t.putInt("NextEntry", nextEntryId);
        ListTag list = new ListTag();
        for (ShopEntry e : entries) list.add(e.save());
        t.put("Entries", list);
        return t;
    }

    public static Shop load(CompoundTag t) {
        Shop s = new Shop(t.getInt("Id"), t.getString("Name"));
        try {
            s.mode = ShopMode.valueOf(t.getString("Mode"));
        } catch (IllegalArgumentException ignored) {
            s.mode = ShopMode.SELL;
        }
        s.allowCash = t.getBoolean("Cash");
        s.allowCard = t.getBoolean("Card");
        s.licence = t.getString("Licence");   // "" pour les anciennes sauvegardes
        s.policeGrade = t.contains("Police") ? Math.max(-1, Math.min(2, t.getInt("Police"))) : -1;
        s.nextEntryId = Math.max(1, t.getInt("NextEntry"));
        ListTag list = t.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ShopEntry e = ShopEntry.load(list.getCompound(i));
            if (!e.item.isEmpty()) s.entries.add(e);
            s.nextEntryId = Math.max(s.nextEntryId, e.id + 1);
        }
        return s;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("mode", mode.name());
        o.addProperty("allowCash", allowCash);
        o.addProperty("allowCard", allowCard);
        o.addProperty("licence", licence);
        o.addProperty("policeGrade", policeGrade);
        JsonArray arr = new JsonArray();
        for (ShopEntry e : entries) arr.add(e.toJson());
        o.add("entries", arr);
        return o;
    }

    public static Shop fromJson(JsonObject o) {
        Shop s = new Shop(o.get("id").getAsInt(), o.has("name") ? o.get("name").getAsString() : "Boutique");
        try {
            if (o.has("mode")) s.mode = ShopMode.valueOf(o.get("mode").getAsString().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            s.mode = ShopMode.SELL;
        }
        if (o.has("allowCash")) s.allowCash = o.get("allowCash").getAsBoolean();
        if (o.has("allowCard")) s.allowCard = o.get("allowCard").getAsBoolean();
        if (o.has("licence")) s.licence = o.get("licence").getAsString();
        if (o.has("policeGrade")) s.policeGrade = Math.max(-1, Math.min(2, o.get("policeGrade").getAsInt()));
        if (o.has("entries")) {
            for (JsonElement el : o.getAsJsonArray("entries")) {
                ShopEntry e = ShopEntry.fromJson(el.getAsJsonObject());
                if (e == null) {
                    ShopData.LOGGER.warn("[Boutiques] Article ignoré dans « {} » (objet ou NBT invalide) : {}", s.name, el);
                    continue;
                }
                if (e.id <= 0 || s.entry(e.id) != null) {   // id absent ou en double -> on en attribue un nouveau
                    String cat = e.category;
                    e = new ShopEntry(s.nextEntryId, e.item, e.quantity, e.price);
                    e.category = cat;
                }
                s.entries.add(e);
                s.nextEntryId = Math.max(s.nextEntryId, e.id + 1);
            }
        }
        return s;
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeUtf(name, 64);
        b.writeEnum(mode);
        b.writeBoolean(allowCash);
        b.writeBoolean(allowCard);
        b.writeUtf(licence, 64);
        b.writeByte(policeGrade);
        b.writeVarInt(entries.size());
        for (ShopEntry e : entries) e.write(b);
    }

    public static Shop read(FriendlyByteBuf b) {
        Shop s = new Shop(b.readVarInt(), b.readUtf(64));
        s.mode = b.readEnum(ShopMode.class);
        s.allowCash = b.readBoolean();
        s.allowCard = b.readBoolean();
        s.licence = b.readUtf(64);
        s.policeGrade = b.readByte();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) s.entries.add(ShopEntry.read(b));
        return s;
    }
}
