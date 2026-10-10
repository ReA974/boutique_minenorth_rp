package com.minenorth_shops;

import com.minenorth_shops.packet.AdminEditPacket;
import com.minenorth_shops.packet.AdminSyncPacket;
import com.minenorth_shops.packet.ShopActionPacket;
import com.minenorth_shops.packet.ShopStatePacket;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Network {
    private static final String PROTOCOL = "5";   // 5 : boutiques pompiers + illégales (4 : rubriques des articles, 3 : boutiques réservées à la police)
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthShops.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Boutique ouverte par chaque joueur. entityId = -1 : ouverte par commande / admin (pas de contrôle de distance). */
    public record Session(int shopId, int entityId) {}

    public static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    public static final Set<UUID> WATCH_ADMIN = ConcurrentHashMap.newKeySet();

    private Network() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ShopStatePacket.class, ShopStatePacket::encode, ShopStatePacket::decode, ShopStatePacket::handle);
        CHANNEL.registerMessage(id++, ShopActionPacket.class, ShopActionPacket::encode, ShopActionPacket::decode, ShopActionPacket::handle);
        CHANNEL.registerMessage(id++, AdminSyncPacket.class, AdminSyncPacket::encode, AdminSyncPacket::decode, AdminSyncPacket::handle);
        CHANNEL.registerMessage(id++, AdminEditPacket.class, AdminEditPacket::encode, AdminEditPacket::decode, AdminEditPacket::handle);
    }

    // ---------- boutique côté joueur ----------

    public static void openShop(ServerPlayer p, Shop shop, int entityId) {
        // Boutique police : les autres joueurs ne peuvent même pas l'ouvrir (les ops peuvent la consulter, pas y acheter).
        String police = PompierCompat.denial(p, shop);
        if (police == null) police = PoliceCompat.denial(p, shop);
        if (police != null && !p.hasPermissions(2)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("§c" + police), true);
            return;
        }
        SESSIONS.put(p.getUUID(), new Session(shop.id, entityId));
        send(p, ShopStatePacket.compute(p, shop, true, ""));
    }

    public static void sendShop(ServerPlayer p, String message) {
        Shop shop = sessionShop(p);
        if (shop == null) closeShop(p, "");
        else send(p, ShopStatePacket.compute(p, shop, false, message));
    }

    public static void closeShop(ServerPlayer p, String message) {
        if (SESSIONS.remove(p.getUUID()) != null) send(p, ShopStatePacket.closed(message));
    }

    /** Boutique de la session si elle est toujours valide (boutique existante, vendeur présent et assez proche). */
    @Nullable
    public static Shop sessionShop(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return null;
        Shop shop = ShopData.get(p.server).shop(s.shopId());
        if (shop == null) return null;
        if (s.entityId() >= 0) {
            Entity e = p.level().getEntity(s.entityId());
            double max = ShopConfig.MAX_DISTANCE.get();
            if (e == null || !e.isAlive() || EntityLinks.shopOf(e) != shop.id || p.distanceToSqr(e) > max * max) return null;
        }
        return shop;
    }

    // ---------- administration ----------

    public static void openAdmin(ServerPlayer p, int focusShop, String message) {
        if (!p.hasPermissions(2)) return;
        WATCH_ADMIN.add(p.getUUID());
        send(p, AdminSyncPacket.compute(p.server, true, focusShop, message));
    }

    public static void sendAdmin(ServerPlayer p, String message) {
        send(p, AdminSyncPacket.compute(p.server, false, -1, message));
    }

    // ---------- rafraîchissement ----------

    /** shopsChanged : les boutiques ont été modifiées (pousse aux admins). Sinon : simple mise à jour espèces/solde. */
    public static void refresh(MinecraftServer server, boolean shopsChanged) {
        if (SESSIONS.isEmpty() && (WATCH_ADMIN.isEmpty() || !shopsChanged)) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (SESSIONS.containsKey(id)) sendShop(p, "");
            if (shopsChanged && WATCH_ADMIN.contains(id)) {
                if (p.hasPermissions(2)) sendAdmin(p, "");
                else WATCH_ADMIN.remove(id);
            }
        }
    }

    public static void forget(UUID id) {
        SESSIONS.remove(id);
        WATCH_ADMIN.remove(id);
    }

    public static void send(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }
}
