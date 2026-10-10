package com.minenorth_shops;

import com.minenorth_shops.shop.IllegalData;
import com.minenorth_shops.shop.IllegalData.Slot;
import com.minenorth_shops.shop.IllegalData.State;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopData;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Boutiques illégales : plusieurs entités (en pratique 2) sont assignées à la même boutique, une seule est présente à la fois.
 * Toutes les {@code intervalleMinutes}, la visible disparaît et la suivante apparaît, sauf si un joueur est trop près.
 *
 * Une entité cachée est retirée du monde et ses données complètes (NBT) sont gardées dans {@link IllegalData} ; elle est recréée à son
 * emplacement quand son tour revient. L'état (emplacement actif, échéance) est la source de vérité : une entité qui se charge alors que
 * ce n'est pas son tour est retirée à l'instant (EntityJoinLevelEvent), et un emplacement actif dont l'entité est cachée est recréé dès
 * que son chunk est chargé. Les chunks non chargés ne posent donc aucun problème.
 */
@Mod.EventBusSubscriber(modid = MineNorthShops.MODID)
public final class IllegalRotation {
    private static final Logger LOG = LogUtils.getLogger();
    /** Numéro d'emplacement mémorisé dans les données persistantes de l'entité (il suit l'entité quand elle est recréée). */
    private static final String KEY_SLOT = "minenorth_shops:slot";

    /** boutique -> emplacement -> entité actuellement chargée (non persisté : rempli au chargement des entités). */
    private static final Map<Integer, Map<Integer, UUID>> LIVE = new HashMap<>();
    private static int tickCounter;

    private IllegalRotation() {}

    public static boolean enabled() { return ShopConfig.ILLEGAL_ENABLED.get(); }

    private static boolean rotating(State st) { return st.slots.size() >= 2; }

    // ------------------------------------------------------------------ liaison

    /** Appelé quand une entité est assignée à une boutique (EntityLinks.link). */
    public static void onLinked(Entity e, int shopId) {
        MinecraftServer server = e.getServer();
        if (server == null) return;
        Shop shop = ShopData.get(server).shop(shopId);
        if (shop != null && shop.illegal) register(server, e, shop);
    }

    /** Appelé quand l'entité n'est plus une boutique (EntityLinks.unlink) : son emplacement disparaît. */
    public static void onUnlinked(Entity e, int shopId) {
        MinecraftServer server = e.getServer();
        CompoundTag t = e.getPersistentData();
        if (server == null || !t.contains(KEY_SLOT)) return;
        int slotId = t.getInt(KEY_SLOT);
        t.remove(KEY_SLOT);
        IllegalData d = IllegalData.get(server);
        State st = d.states.get(shopId);
        if (st == null) return;
        st.slots.remove(slotId);
        Map<Integer, UUID> live = LIVE.get(shopId);
        if (live != null) live.remove(slotId);
        if (st.active == slotId) st.active = st.slots.isEmpty() ? -1 : st.slots.keySet().iterator().next();
        d.setDirty();
    }

    /** Enregistre (ou met à jour) l'emplacement de cette entité. */
    private static Slot register(MinecraftServer server, Entity e, Shop shop) {
        IllegalData d = IllegalData.get(server);
        State st = d.state(shop.id);
        CompoundTag t = e.getPersistentData();
        int slotId = t.contains(KEY_SLOT) ? t.getInt(KEY_SLOT) : -1;
        Slot slot = slotId >= 0 ? st.slots.get(slotId) : null;
        if (slot == null) {
            slot = new Slot();
            slot.id = slotId >= 0 && !st.slots.containsKey(slotId) ? slotId : st.nextSlot;
            st.nextSlot = Math.max(st.nextSlot, slot.id + 1);
            st.slots.put(slot.id, slot);
            t.putInt(KEY_SLOT, slot.id);
        }
        slot.hidden = false;
        slot.nbt = null;
        slot.dim = e.level().dimension().location().toString();
        slot.x = e.getX();
        slot.y = e.getY();
        slot.z = e.getZ();
        if (!st.slots.containsKey(st.active)) st.active = slot.id;
        if (st.lastSwap == 0) st.lastSwap = System.currentTimeMillis();
        LIVE.computeIfAbsent(shop.id, k -> new HashMap<>()).put(slot.id, e.getUUID());
        d.setDirty();
        return slot;
    }

    // ------------------------------------------------------------------ chargement des entités

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent ev) {
        Entity e = ev.getEntity();
        if (ev.getLevel().isClientSide() || e instanceof Player) return;
        int shopId = EntityLinks.shopOf(e);
        if (shopId < 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) return;
        try {
            Shop shop = ShopData.get(server).shop(shopId);
            if (shop == null || !shop.illegal || !enabled()) return;
            Slot slot = register(server, e, shop);
            State st = IllegalData.get(server).state(shopId);
            if (rotating(st) && slot.id != st.active && hide(server, e, st, slot)) ev.setCanceled(true);
        } catch (RuntimeException ex) {
            LOG.warn("[Boutiques] Boutique illégale : entité {} ignorée", e, ex);
        }
    }

    /** Retire l'entité du monde en gardant ses données. true si c'est fait (l'appelant la retire du monde ou annule son arrivée). */
    private static boolean hide(MinecraftServer server, Entity e, State st, Slot slot) {
        CompoundTag tag = new CompoundTag();
        if (!e.save(tag)) {
            LOG.warn("[Boutiques] Entité {} non sauvegardable : elle reste visible.", e);
            return false;
        }
        tag.remove("UUID");
        slot.nbt = tag;
        slot.hidden = true;
        slot.dim = e.level().dimension().location().toString();
        slot.x = e.getX();
        slot.y = e.getY();
        slot.z = e.getZ();
        for (Map<Integer, UUID> m : LIVE.values()) m.values().remove(e.getUUID());
        IllegalData.get(server).setDirty();
        return true;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        if (++tickCounter < ShopConfig.ILLEGAL_CHECK_SECONDS.get() * 20) return;
        tickCounter = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) return;
        try {
            tick(server);
        } catch (RuntimeException ex) {
            LOG.error("[Boutiques] Boutiques illégales : erreur", ex);
        }
    }

    private static long scanCountdown;

    private static void tick(MinecraftServer server) {
        ShopData sd = ShopData.get(server);
        IllegalData d = IllegalData.get(server);
        boolean on = enabled();

        // états de boutiques supprimées
        for (Integer id : new ArrayList<>(d.states.keySet())) {
            if (sd.shop(id) == null) {
                d.states.remove(id);
                LIVE.remove(id);
                d.setDirty();
                LOG.warn("[Boutiques] Boutique #{} supprimée : état illégal effacé.", id);
            }
        }

        // boutiques illégales dont toutes les entités ne sont pas encore connues : un balayage des entités chargées (toutes les 10 s)
        Set<Integer> needScan = new HashSet<>();
        for (Shop shop : sd.all()) {
            State st = d.states.get(shop.id);
            if (shop.illegal && on && (st == null || st.slots.size() < 2)) needScan.add(shop.id);
        }
        if (!needScan.isEmpty() && --scanCountdown <= 0) {
            scanCountdown = Math.max(1, 10 / ShopConfig.ILLEGAL_CHECK_SECONDS.get());
            scan(server, sd, needScan);
        }

        for (Shop shop : sd.all()) {
            State st = d.states.get(shop.id);
            if (st == null) continue;
            if (!shop.illegal || !on) {
                restoreAll(server, d, shop, st);
                continue;
            }
            reconcile(server, d, shop, st);
        }
    }

    private static void scan(MinecraftServer server, ShopData sd, Set<Integer> shopIds) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                int id = EntityLinks.shopOf(e);
                if (id < 0 || !shopIds.contains(id) || e instanceof Player) continue;
                Shop shop = sd.shop(id);
                if (shop != null) register(server, e, shop);
            }
        }
    }

    /** Boutique redevenue légale (ou rotation coupée) : toutes les entités cachées reviennent. */
    private static void restoreAll(MinecraftServer server, IllegalData d, Shop shop, State st) {
        boolean pending = false;
        for (Slot slot : st.slots.values()) {
            if (slot.hidden && !spawn(server, d, slot)) pending = true;
        }
        if (!pending && !shop.illegal) {
            d.states.remove(shop.id);
            LIVE.remove(shop.id);
            d.setDirty();
        }
    }

    private static void reconcile(MinecraftServer server, IllegalData d, Shop shop, State st) {
        if (st.slots.isEmpty()) return;
        if (!st.slots.containsKey(st.active)) {
            st.active = st.slots.keySet().iterator().next();
            d.setDirty();
        }
        if (!rotating(st)) {   // une seule entité connue : rien à alterner, elle reste visible
            for (Slot slot : st.slots.values()) if (slot.hidden) spawn(server, d, slot);
            return;
        }
        applyState(server, d, shop, st);

        long now = System.currentTimeMillis();
        long interval = (shop.illegalMinutes > 0 ? shop.illegalMinutes : ShopConfig.ILLEGAL_MINUTES.get()) * 60_000L;
        if (st.lastSwap == 0) {
            st.lastSwap = now;
            d.setDirty();
        }
        if (now - st.lastSwap < interval) return;
        int next = nextSlot(st);
        if (playerNear(server, shop, st.slots.get(st.active), st.slots.get(next))) return;   // on réessaie au prochain contrôle
        swap(server, d, shop, st, next);
    }

    /** Cache les entités chargées qui ne sont pas à l'emplacement actif, recrée celle de l'emplacement actif si elle est cachée. */
    private static void applyState(MinecraftServer server, IllegalData d, Shop shop, State st) {
        Map<Integer, UUID> live = LIVE.get(shop.id);
        if (live != null) {
            for (Map.Entry<Integer, UUID> en : new ArrayList<>(live.entrySet())) {
                if (en.getKey() == st.active) continue;
                Entity e = find(server, st.slots.get(en.getKey()), en.getValue());
                if (e == null) {
                    live.remove(en.getKey());
                    continue;
                }
                Slot slot = st.slots.get(en.getKey());
                if (slot != null && hide(server, e, st, slot)) {
                    live.remove(en.getKey());
                    e.discard();
                }
            }
        }
        Slot active = st.slots.get(st.active);
        if (active != null && active.hidden) spawn(server, d, active);
    }

    private static int nextSlot(State st) {
        boolean after = false;
        for (int id : st.slots.keySet()) {
            if (after) return id;
            if (id == st.active) after = true;
        }
        return st.slots.keySet().iterator().next();
    }

    private static void swap(MinecraftServer server, IllegalData d, Shop shop, State st, int next) {
        st.active = next;
        st.lastSwap = System.currentTimeMillis();
        d.setDirty();
        applyState(server, d, shop, st);
        LOG.info("[Boutiques] « {} » (#{}) : emplacement #{} actif.", shop.name, shop.id, next);
    }

    /** Changement immédiat (commande /shops illegal <id> basculer), sans attendre l'échéance ni tenir compte des joueurs. */
    public static boolean forceSwap(MinecraftServer server, Shop shop) {
        IllegalData d = IllegalData.get(server);
        State st = d.states.get(shop.id);
        if (st == null || !rotating(st) || !shop.illegal) return false;
        swap(server, d, shop, st, nextSlot(st));
        return true;
    }

    /** Résumé pour /shops illegal <id> : emplacements, actif, minutes avant le prochain changement (-1 si pas de rotation). */
    public static String status(MinecraftServer server, Shop shop) {
        State st = IllegalData.get(server).states.get(shop.id);
        if (!shop.illegal) return "boutique légale";
        if (st == null || st.slots.isEmpty()) return "aucune entité connue (assignez-en 2 avec l'outil de liaison)";
        StringBuilder b = new StringBuilder(st.slots.size() + " emplacement(s) : ");
        for (Slot s : st.slots.values()) {
            b.append("#").append(s.id).append(s.id == st.active ? " (actif" : " (");
            if (s.hidden) b.append(s.id == st.active ? ", en attente de chargement" : "caché");
            else if (s.id != st.active) b.append("chargé, sera caché");
            b.append(") ");
        }
        if (rotating(st)) {
            long interval = (shop.illegalMinutes > 0 ? shop.illegalMinutes : ShopConfig.ILLEGAL_MINUTES.get()) * 60_000L;
            long left = Math.max(0, st.lastSwap + interval - System.currentTimeMillis());
            b.append("— prochain changement dans ").append(left / 60_000).append(" min").append(enabled() ? "" : " (rotation désactivée dans la config)");
        } else {
            b.append("— une seule entité : pas d'alternance");
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ utilitaires

    private static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    private static Entity find(MinecraftServer server, Slot slot, UUID id) {
        if (slot == null) return null;
        ServerLevel lvl = level(server, slot.dim);
        Entity e = lvl == null ? null : lvl.getEntity(id);
        return e != null && e.isAlive() ? e : null;
    }

    /** Recrée l'entité cachée à son emplacement si son chunk est chargé. true si faite (ou impossible à refaire). */
    private static boolean spawn(MinecraftServer server, IllegalData d, Slot slot) {
        ServerLevel lvl = level(server, slot.dim);
        if (lvl == null) return false;
        if (!lvl.hasChunkAt(BlockPos.containing(slot.x, slot.y, slot.z))) return false;   // chunk non chargé : plus tard
        CompoundTag tag = slot.nbt.copy();
        Entity e = EntityType.loadEntityRecursive(tag, lvl, ent -> ent);
        slot.hidden = false;
        slot.nbt = null;
        d.setDirty();
        if (e == null) {
            LOG.warn("[Boutiques] Impossible de recréer l'entité de l'emplacement #{} (type disparu ?).", slot.id);
            return true;
        }
        lvl.addFreshEntity(e);
        return true;
    }

    private static boolean playerNear(MinecraftServer server, Shop shop, Slot... slots) {
        double r = shop.illegalRadius >= 0 ? shop.illegalRadius : ShopConfig.ILLEGAL_RADIUS.get();
        if (r <= 0) return false;
        double r2 = r * r;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            if (p.isSpectator()) continue;
            String dim = p.level().dimension().location().toString();
            for (Slot s : slots) {
                if (s == null || !s.dim.equals(dim)) continue;
                double dx = p.getX() - s.x, dy = p.getY() - s.y, dz = p.getZ() - s.z;
                if (dx * dx + dy * dy + dz * dz <= r2) return true;
            }
        }
        return false;
    }
}
