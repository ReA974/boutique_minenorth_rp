package com.minenorth_shops.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Toutes les boutiques du serveur (sauvegardées dans world/data/minenorth_shops.dat). */
public class ShopData extends SavedData {
    private static final String NAME = "minenorth_shops";

    private final Map<Integer, Shop> shops = new TreeMap<>();
    private int nextId = 1;
    private boolean changed;

    public static ShopData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ShopData::load, ShopData::new, NAME);
    }

    public static ShopData load(CompoundTag tag) {
        ShopData d = new ShopData();
        d.nextId = Math.max(1, tag.getInt("NextId"));
        ListTag list = tag.getList("Shops", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Shop s = Shop.load(list.getCompound(i));
            d.shops.put(s.id, s);
            d.nextId = Math.max(d.nextId, s.id + 1);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextId", nextId);
        ListTag list = new ListTag();
        for (Shop s : shops.values()) list.add(s.save());
        tag.put("Shops", list);
        return tag;
    }

    /** À appeler après toute modification : sauvegarde + rafraîchissement des écrans ouverts. */
    public void changed() {
        setDirty();
        changed = true;
    }

    public boolean consumeChanged() {
        boolean c = changed;
        changed = false;
        return c;
    }

    public Shop create(String name) {
        Shop s = new Shop(nextId++, name);
        shops.put(s.id, s);
        changed();
        return s;
    }

    public boolean delete(int id) {
        boolean r = shops.remove(id) != null;
        if (r) changed();
        return r;
    }

    @Nullable
    public Shop shop(int id) {
        return shops.get(id);
    }

    public Collection<Shop> all() {
        return Collections.unmodifiableCollection(shops.values());
    }

    public List<Integer> ids() {
        return new ArrayList<>(shops.keySet());
    }
}
