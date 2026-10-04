package com.minenorth_shops.packet;

import com.minenorth_shops.client.ClientHooks;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur -> client (admins) : liste complète des boutiques. */
public class AdminSyncPacket {
    public boolean open;
    /** Boutique à ouvrir directement en édition (-1 = aucune). */
    public int focus = -1;
    public String message = "";
    public List<Shop> shops = new ArrayList<>();

    public static AdminSyncPacket compute(MinecraftServer server, boolean open, int focus, String message) {
        AdminSyncPacket m = new AdminSyncPacket();
        m.open = open;
        m.focus = focus;
        m.message = message;
        m.shops.addAll(ShopData.get(server).all());
        return m;
    }

    public static void encode(AdminSyncPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeInt(m.focus);
        b.writeUtf(m.message, 1024);
        b.writeVarInt(m.shops.size());
        for (Shop s : m.shops) s.write(b);
    }

    public static AdminSyncPacket decode(FriendlyByteBuf b) {
        AdminSyncPacket m = new AdminSyncPacket();
        m.open = b.readBoolean();
        m.focus = b.readInt();
        m.message = b.readUtf(1024);
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) m.shops.add(Shop.read(b));
        return m;
    }

    public static void handle(AdminSyncPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleAdmin(m)));
        ctx.setPacketHandled(true);
    }
}
