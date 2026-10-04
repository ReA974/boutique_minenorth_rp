package com.minenorth_shops.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Un article : un objet (avec son NBT), une quantité par lot et un prix par lot (en centimes). */
public class ShopEntry {
    public final int id;
    public ItemStack item;
    public int quantity;
    public long price;

    public ShopEntry(int id, ItemStack item, int quantity, long price) {
        this.id = id;
        this.item = item.copyWithCount(1);
        this.quantity = quantity;
        this.price = price;
    }

    /** L'objet du joueur correspond-il à cet article ? (NBT comparé seulement si l'article en a un) */
    public boolean matches(ItemStack s) {
        if (s.isEmpty() || !ItemStack.isSameItem(item, s)) return false;
        if (item.hasTag()) return ItemStack.isSameItemSameTags(item, s);
        return !s.isDamaged();   // pas de rachat d'outils abîmés au prix du neuf
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", id);
        t.put("Item", item.save(new CompoundTag()));
        t.putInt("Qty", quantity);
        t.putLong("Price", price);
        return t;
    }

    public static ShopEntry load(CompoundTag t) {
        return new ShopEntry(t.getInt("Id"), ItemStack.of(t.getCompound("Item")), Math.max(1, t.getInt("Qty")), t.getLong("Price"));
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeItem(item);
        b.writeVarInt(quantity);
        b.writeVarLong(price);
    }

    public static ShopEntry read(FriendlyByteBuf b) {
        return new ShopEntry(b.readVarInt(), b.readItem(), b.readVarInt(), b.readVarLong());
    }
}
