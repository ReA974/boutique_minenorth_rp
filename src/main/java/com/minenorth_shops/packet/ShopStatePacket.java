package com.minenorth_shops.packet;

import com.minenorth_eurobank.Money;
import fr.minenorth.api.MineNorth;
import fr.minenorth.api.PayResult;
import com.minenorth_shops.PermisCompat;
import com.minenorth_shops.PoliceCompat;
import com.minenorth_shops.PompierCompat;
import com.minenorth_shops.ShopConfig;
import com.minenorth_shops.client.ClientHooks;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Serveur -> client : contenu de la boutique ouverte + état du joueur (espèces, compte, carte). */
public class ShopStatePacket {
    public boolean open, close;
    @Nullable public Shop shop;
    public long cash, balance;
    public boolean hasAccount;
    /** Vide si la carte est utilisable, sinon la raison (pas de carte, carte d'un autre...). */
    public String cardIssue = "";
    /** Nombre d'objets correspondants dans l'inventaire, par article (même ordre que shop.entries). */
    public int[] have = new int[0];
    public int maxLots = 64;
    public String message = "";
    /** Nom de la licence exigée (vide = aucune) et si le joueur l'a. */
    public String licenceName = "";
    public boolean licenceOk = true;

    public static ShopStatePacket closed(String message) {
        ShopStatePacket m = new ShopStatePacket();
        m.close = true;
        m.message = message;
        return m;
    }

    public static ShopStatePacket compute(ServerPlayer p, Shop shop, boolean open, String message) {
        ShopStatePacket m = new ShopStatePacket();
        m.open = open;
        m.shop = shop;
        m.cash = Money.cashIn(p);
        m.hasAccount = MineNorth.bank().hasAccount(p.server, p.getUUID());
        m.balance = MineNorth.bank().balance(p.server, p.getUUID());
        PayResult r = MineNorth.bank().check(p, 1);
        m.cardIssue = r == PayResult.OK || r == PayResult.INSUFFICIENT_FUNDS ? "" : r.message();
        m.have = new int[shop.entries.size()];
        for (int i = 0; i < m.have.length; i++) m.have[i] = ShopService.countMatching(p, shop.entries.get(i));
        m.maxLots = ShopConfig.MAX_LOTS.get();
        if (shop.requiresLicence()) {
            m.licenceName = PermisCompat.name(shop.licence);
            m.licenceOk = PermisCompat.has(p, shop.licence);
            if (open && !m.licenceOk && message.isEmpty()) message = PermisCompat.denial(p, shop.licence);
        }
        if (open && message.isEmpty()) {
            String police = PompierCompat.denial(p, shop);
            if (police == null) police = PoliceCompat.denial(p, shop);
            if (police != null) message = police;
        }
        m.message = message;
        return m;
    }

    public static void encode(ShopStatePacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.close);
        b.writeUtf(m.message, 1024);
        if (m.close) return;
        b.writeBoolean(m.open);
        m.shop.write(b);
        b.writeVarLong(m.cash);
        b.writeVarLong(m.balance);
        b.writeBoolean(m.hasAccount);
        b.writeUtf(m.cardIssue, 128);
        b.writeVarIntArray(m.have);
        b.writeVarInt(m.maxLots);
        b.writeUtf(m.licenceName, 128);
        b.writeBoolean(m.licenceOk);
    }

    public static ShopStatePacket decode(FriendlyByteBuf b) {
        ShopStatePacket m = new ShopStatePacket();
        m.close = b.readBoolean();
        m.message = b.readUtf(1024);
        if (m.close) return m;
        m.open = b.readBoolean();
        m.shop = Shop.read(b);
        m.cash = b.readVarLong();
        m.balance = b.readVarLong();
        m.hasAccount = b.readBoolean();
        m.cardIssue = b.readUtf(128);
        m.have = b.readVarIntArray();
        m.maxLots = b.readVarInt();
        m.licenceName = b.readUtf(128);
        m.licenceOk = b.readBoolean();
        return m;
    }

    public static void handle(ShopStatePacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleShop(m)));
        ctx.setPacketHandled(true);
    }
}
