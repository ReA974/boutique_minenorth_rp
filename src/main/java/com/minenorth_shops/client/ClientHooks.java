package com.minenorth_shops.client;

import com.minenorth_shops.packet.AdminSyncPacket;
import com.minenorth_shops.packet.ShopStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {}

    public static void handleShop(ShopStatePacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.close) {
            if (mc.screen instanceof ShopScreen s) s.closeFromServer();
            if (!m.message.isEmpty() && mc.player != null) mc.player.displayClientMessage(Component.literal(m.message), true);
            return;
        }
        if (mc.screen instanceof ShopScreen s) {
            if (s.shopId() == m.shop.id) s.update(m);   // même boutique : on garde l'écran (et la session serveur)
            else if (m.open) {
                s.silent();
                mc.setScreen(new ShopScreen(m));
            }
        } else if (m.open) {
            mc.setScreen(new ShopScreen(m));
        }
    }

    public static void handleAdmin(AdminSyncPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ShopAdminScreen s) s.update(m);
        else if (m.open) mc.setScreen(new ShopAdminScreen(m));
    }
}
