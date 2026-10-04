package com.minenorth_shops;

import com.minenorth_shops.items.ShopLinkerItem;
import com.minenorth_shops.shop.Shop;
import com.minenorth_shops.shop.ShopData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liaison boutique <-> entité. L'id de la boutique est stocké dans les données persistantes de l'entité,
 * donc n'importe quelle entité (villageois, PNJ d'un autre mod, armor stand, cadre...) peut devenir vendeur.
 */
public final class EntityLinks {
    public static final String KEY = "minenorth_shops:shop";
    private static final String KEY_PROTECTED = "minenorth_shops:protected";
    private static final String KEY_FROZEN = "minenorth_shops:frozen";

    /** Anti-doublon : un clic droit déclenche parfois deux événements (EntityInteractSpecific puis EntityInteract). */
    private static final Map<UUID, Long> LAST_USE = new ConcurrentHashMap<>();

    public static int shopOf(Entity e) {
        CompoundTag t = e.getPersistentData();
        return t.contains(KEY) ? t.getInt(KEY) : -1;
    }

    public static void link(Entity e, int shopId) {
        CompoundTag t = e.getPersistentData();
        t.putInt(KEY, shopId);
        if (ShopConfig.PROTECT_ENTITIES.get() && !e.isInvulnerable()) {
            e.setInvulnerable(true);
            t.putBoolean(KEY_PROTECTED, true);
        }
        if (e instanceof Mob m) {
            m.setPersistenceRequired();   // ne despawn jamais
            if (ShopConfig.FREEZE_ENTITIES.get() && !m.isNoAi()) {
                m.setNoAi(true);
                t.putBoolean(KEY_FROZEN, true);
            }
        }
    }

    public static boolean unlink(Entity e) {
        CompoundTag t = e.getPersistentData();
        if (!t.contains(KEY)) return false;
        t.remove(KEY);
        if (t.getBoolean(KEY_PROTECTED)) e.setInvulnerable(false);
        if (t.getBoolean(KEY_FROZEN) && e instanceof Mob m) m.setNoAi(false);
        t.remove(KEY_PROTECTED);
        t.remove(KEY_FROZEN);
        return true;
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (handle(e.getEntity(), e.getTarget(), e.getHand())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        if (handle(e.getEntity(), e.getTarget(), e.getHand())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    public static void forget(UUID player) {
        LAST_USE.remove(player);
    }

    /** @return true si l'interaction est consommée par le mod (on annule alors le comportement normal). */
    private static boolean handle(Player player, Entity target, InteractionHand hand) {
        if (!(player instanceof ServerPlayer p)) return false;   // le client n'a pas les données de l'entité
        ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
        boolean admin = p.hasPermissions(2);
        int linked = shopOf(target);

        // outil de liaison (admins)
        if (admin && held.getItem() instanceof ShopLinkerItem) {
            if (hand != InteractionHand.MAIN_HAND || !firstUse(p)) return true;
            if (p.isShiftKeyDown()) {
                p.sendSystemMessage(Component.literal(unlink(target)
                        ? "[Boutiques] Boutique retirée de cette entité."
                        : "[Boutiques] Cette entité n'a pas de boutique."));
                return true;
            }
            int id = ShopLinkerItem.shopId(held);
            Shop shop = id < 0 ? null : ShopData.get(p.server).shop(id);
            if (shop == null) {
                p.sendSystemMessage(Component.literal("[Boutiques] Outil non configuré : récupérez-le depuis /shops."));
                return true;
            }
            link(target, id);
            p.sendSystemMessage(Component.literal("[Boutiques] « " + shop.name + " » (#" + id + ") assignée à "
                    + target.getDisplayName().getString() + "."));
            return true;
        }

        if (linked < 0) return false;
        Shop shop = ShopData.get(p.server).shop(linked);
        if (shop == null) {
            if (admin && hand == InteractionHand.MAIN_HAND && firstUse(p)) {
                p.sendSystemMessage(Component.literal("[Boutiques] La boutique #" + linked
                        + " n'existe plus. Accroupi + clic droit avec l'outil pour délier."));
            }
            return false;
        }
        if (hand != InteractionHand.MAIN_HAND || !firstUse(p)) return true;

        // admin accroupi : édition directe de la boutique
        if (admin && p.isShiftKeyDown()) Network.openAdmin(p, linked, "");
        else Network.openShop(p, shop, target.getId());
        return true;
    }

    private static boolean firstUse(ServerPlayer p) {
        long now = p.level().getGameTime();
        Long last = LAST_USE.put(p.getUUID(), now);
        return last == null || last != now;
    }
}
