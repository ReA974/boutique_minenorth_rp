package com.minenorth_shops.shop;

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
        s.nextEntryId = Math.max(1, t.getInt("NextEntry"));
        ListTag list = t.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ShopEntry e = ShopEntry.load(list.getCompound(i));
            if (!e.item.isEmpty()) s.entries.add(e);
            s.nextEntryId = Math.max(s.nextEntryId, e.id + 1);
        }
        return s;
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeUtf(name, 64);
        b.writeEnum(mode);
        b.writeBoolean(allowCash);
        b.writeBoolean(allowCard);
        b.writeVarInt(entries.size());
        for (ShopEntry e : entries) e.write(b);
    }

    public static Shop read(FriendlyByteBuf b) {
        Shop s = new Shop(b.readVarInt(), b.readUtf(64));
        s.mode = b.readEnum(ShopMode.class);
        s.allowCash = b.readBoolean();
        s.allowCard = b.readBoolean();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) s.entries.add(ShopEntry.read(b));
        return s;
    }
}
