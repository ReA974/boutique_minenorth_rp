package com.minenorth_shops.shop;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Un article : un objet (avec son NBT), une quantité par lot et un prix par lot (en centimes). */
public class ShopEntry {
    public final int id;
    public ItemStack item;
    public int quantity;
    public long price;
    /** Rubrique de la boutique (« Armes », « Boissons »...) ; vide = sans rubrique. */
    public String category = "";
    public static final int MAX_CATEGORY = 20;

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
        if (!category.isEmpty()) t.putString("Category", category);
        return t;
    }

    public static ShopEntry load(CompoundTag t) {
        ShopEntry e = new ShopEntry(t.getInt("Id"), ItemStack.of(t.getCompound("Item")), Math.max(1, t.getInt("Qty")), t.getLong("Price"));
        e.category = t.getString("Category");
        return e;
    }

    /** Format JSON : {"id":1,"item":"minecraft:iron_sword","nbt":"{...}","quantity":1,"price":1500} (prix en centimes). */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item.getItem());
        o.addProperty("item", key == null ? "minecraft:air" : key.toString());
        if (item.getTag() != null && !item.getTag().isEmpty()) o.addProperty("nbt", item.getTag().toString());
        o.addProperty("quantity", quantity);
        o.addProperty("price", price);
        if (!category.isEmpty()) o.addProperty("category", category);
        return o;
    }

    /** @return null si l'objet est inconnu (mod absent, faute de frappe...) ou le NBT invalide. */
    public static ShopEntry fromJson(JsonObject o) {
        ResourceLocation rl = ResourceLocation.tryParse(o.get("item").getAsString());
        Item it = rl == null ? Items.AIR : ForgeRegistries.ITEMS.getValue(rl);
        if (it == null || it == Items.AIR) return null;
        ItemStack st = new ItemStack(it);
        try {
            if (o.has("nbt")) st.setTag(TagParser.parseTag(o.get("nbt").getAsString()));
        } catch (Exception ex) {
            return null;
        }
        int qty = o.has("quantity") ? Math.max(1, o.get("quantity").getAsInt()) : 1;
        long price = o.has("price") ? Math.max(0, o.get("price").getAsLong()) : 0;
        ShopEntry e = new ShopEntry(o.has("id") ? o.get("id").getAsInt() : 0, st, qty, price);
        if (o.has("category")) e.category = clean(o.get("category").getAsString());
        return e;
    }

    /** Nom de rubrique propre : sans codes de formatage ni caractères de contrôle, 20 caractères au plus. */
    public static String clean(String s) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}§]", "").trim();
        return s.length() > MAX_CATEGORY ? s.substring(0, MAX_CATEGORY).trim() : s;
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeItem(item);
        b.writeVarInt(quantity);
        b.writeVarLong(price);
        b.writeUtf(category, 32);
    }

    public static ShopEntry read(FriendlyByteBuf b) {
        ShopEntry e = new ShopEntry(b.readVarInt(), b.readItem(), b.readVarInt(), b.readVarLong());
        e.category = b.readUtf(32);
        return e;
    }
}
