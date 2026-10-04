package com.minenorth_shops.items;

import com.minenorth_shops.shop.Shop;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Outil d'admin : clic droit sur une entité = lui assigner la boutique ; accroupi + clic droit = la retirer. */
public class ShopLinkerItem extends Item {
    public ShopLinkerItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    public static ItemStack create(Shop shop) {
        ItemStack s = new ItemStack(ModItems.LINKER.get());
        CompoundTag t = s.getOrCreateTag();
        t.putInt("ShopId", shop.id);
        t.putString("ShopName", shop.name);
        s.setHoverName(Component.literal("Lien boutique : " + shop.name).withStyle(ChatFormatting.GOLD));
        return s;
    }

    public static int shopId(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains("ShopId") ? t.getInt("ShopId") : -1;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return shopId(stack) >= 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("ShopId")) {
            tip.add(Component.literal("Boutique #" + t.getInt("ShopId") + " : " + t.getString("ShopName")).withStyle(ChatFormatting.AQUA));
        } else {
            tip.add(Component.literal("Non configuré : utilisez /shops").withStyle(ChatFormatting.RED));
        }
        tip.add(Component.literal("Clic droit sur une entité : assigner").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Accroupi + clic droit : retirer").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Réservé aux admins (op)").withStyle(ChatFormatting.DARK_GRAY));
    }
}
