package com.minenorth_shops.items;

import com.minenorth_shops.MineNorthShops;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthShops.MODID);

    public static final RegistryObject<Item> LINKER = ITEMS.register("shop_linker", ShopLinkerItem::new);

    private ModItems() {}
}
