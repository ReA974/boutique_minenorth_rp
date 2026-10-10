package com.minenorth_shops.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.TreeMap;

/**
 * État des boutiques illégales (world/data/minenorth_shops_illegal.dat) : emplacements (une entité = un emplacement), emplacement actif,
 * date du dernier changement, et données complètes de l'entité quand elle est cachée (elle est alors retirée du monde).
 */
public class IllegalData extends SavedData {
    private static final String NAME = "minenorth_shops_illegal";

    public static final class Slot {
        public int id;
        public String dim = "";
        public double x, y, z;
        /** Entité retirée du monde : ses données sont dans {@link #nbt}. */
        public boolean hidden;
        public CompoundTag nbt;
    }

    public static final class State {
        /** Emplacement actuellement visible (-1 = aucun). */
        public int active = -1;
        /** Dernier changement (ms, horloge réelle) : l'échéance survit aux redémarrages. */
        public long lastSwap;
        public int nextSlot = 1;
        public final Map<Integer, Slot> slots = new TreeMap<>();
    }

    public final Map<Integer, State> states = new TreeMap<>();

    public static IllegalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(IllegalData::load, IllegalData::new, NAME);
    }

    public State state(int shopId) {
        return states.computeIfAbsent(shopId, k -> new State());
    }

    public static IllegalData load(CompoundTag tag) {
        IllegalData d = new IllegalData();
        ListTag list = tag.getList("States", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            State s = new State();
            s.active = t.getInt("Active");
            s.lastSwap = t.getLong("LastSwap");
            s.nextSlot = Math.max(1, t.getInt("NextSlot"));
            ListTag sl = t.getList("Slots", Tag.TAG_COMPOUND);
            for (int j = 0; j < sl.size(); j++) {
                CompoundTag c = sl.getCompound(j);
                Slot slot = new Slot();
                slot.id = c.getInt("Id");
                slot.dim = c.getString("Dim");
                slot.x = c.getDouble("X");
                slot.y = c.getDouble("Y");
                slot.z = c.getDouble("Z");
                slot.hidden = c.getBoolean("Hidden") && c.contains("Nbt", Tag.TAG_COMPOUND);
                if (slot.hidden) slot.nbt = c.getCompound("Nbt");
                s.slots.put(slot.id, slot);
                s.nextSlot = Math.max(s.nextSlot, slot.id + 1);
            }
            d.states.put(t.getInt("Shop"), s);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Integer, State> en : states.entrySet()) {
            State s = en.getValue();
            CompoundTag t = new CompoundTag();
            t.putInt("Shop", en.getKey());
            t.putInt("Active", s.active);
            t.putLong("LastSwap", s.lastSwap);
            t.putInt("NextSlot", s.nextSlot);
            ListTag sl = new ListTag();
            for (Slot slot : s.slots.values()) {
                CompoundTag c = new CompoundTag();
                c.putInt("Id", slot.id);
                c.putString("Dim", slot.dim);
                c.putDouble("X", slot.x);
                c.putDouble("Y", slot.y);
                c.putDouble("Z", slot.z);
                c.putBoolean("Hidden", slot.hidden);
                if (slot.hidden && slot.nbt != null) c.put("Nbt", slot.nbt);
                sl.add(c);
            }
            t.put("Slots", sl);
            list.add(t);
        }
        tag.put("States", list);
        return tag;
    }
}
