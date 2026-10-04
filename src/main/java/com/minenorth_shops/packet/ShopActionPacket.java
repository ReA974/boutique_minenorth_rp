package com.minenorth_shops.packet;

import com.minenorth_eurobank.Money;
import com.minenorth_shops.Network;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopEntry;
import com.minenorth_shops.shop.ShopService;
import com.minenorth_shops.shop.ShopService.Method;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : achat / vente dans la boutique ouverte, ou fermeture. Tout est revalidé côté serveur. */
public class ShopActionPacket {
    public enum Action { TRADE, CLOSE }

    private final Action action;
    private final int entryId;
    private final int lots;          // -1 = maximum possible
    private final long expectedPrice; // refus si le prix a changé entre-temps
    private final Method method;

    public ShopActionPacket(Action action, int entryId, int lots, long expectedPrice, Method method) {
        this.action = action;
        this.entryId = entryId;
        this.lots = lots;
        this.expectedPrice = expectedPrice;
        this.method = method;
    }

    public static ShopActionPacket close() {
        return new ShopActionPacket(Action.CLOSE, 0, 0, 0, Method.CASH);
    }

    public static void encode(ShopActionPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.action);
        b.writeVarInt(m.entryId);
        b.writeInt(m.lots);
        b.writeVarLong(m.expectedPrice);
        b.writeEnum(m.method);
    }

    public static ShopActionPacket decode(FriendlyByteBuf b) {
        return new ShopActionPacket(b.readEnum(Action.class), b.readVarInt(), b.readInt(), b.readVarLong(), b.readEnum(Method.class));
    }

    public static void handle(ShopActionPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p == null) return;
            if (m.action == Action.CLOSE) {
                Network.SESSIONS.remove(p.getUUID());
                return;
            }
            Shop shop = Network.sessionShop(p);
            if (shop == null) {
                Network.closeShop(p, "Vous êtes trop loin du vendeur.");
                return;
            }
            ShopEntry e = shop.entry(m.entryId);
            String msg;
            if (e == null) msg = "Cet article n'est plus disponible.";
            else if (e.price != m.expectedPrice) msg = "Le prix a changé : " + Money.format(e.price) + ".";
            else if (m.lots == 0 || m.lots < -1) msg = "Quantité invalide.";
            else msg = ShopService.transact(p, shop, e, m.lots, m.method);
            Network.sendShop(p, msg);
        });
        ctx.setPacketHandled(true);
    }
}
