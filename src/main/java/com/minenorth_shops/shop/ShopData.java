package com.minenorth_shops.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Toutes les boutiques du serveur.
 * Source de vérité : {@code config/minenorth_shops/shops.json} (modifiable à la main, rechargé automatiquement
 * quand le fichier change). Une copie reste dans world/data/minenorth_shops.dat (migration des anciens mondes).
 */
public class ShopData extends SavedData {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "minenorth_shops";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<Integer, Shop> shops = new TreeMap<>();
    private int nextId = 1;
    private boolean changed;

    /**
     * Date (ms) de la dernière modification faite en jeu, écrite dans le monde ET dans shops.json (« revision »). Sert à reconnaître un
     * shops.json plus ancien que les données du monde (modèle de config redéployé, hébergeur qui restaure config/, sauvegarde ancienne…) :
     * il est alors mis de côté (shops.json.stale-…) au lieu d'écraser les boutiques. Un fichier modifié à la main garde sa revision :
     * il est pris. Pour forcer volontairement un ancien fichier, mettre une « revision » supérieure.
     */
    private long revision;

    private boolean jsonInit;
    private long jsonStamp = -1;   // dernière modif du fichier connue (écrite ou lue par nous)

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("minenorth_shops").resolve("shops.json");
    }

    public static ShopData get(MinecraftServer server) {
        ShopData d = server.overworld().getDataStorage().computeIfAbsent(ShopData::load, ShopData::new, NAME);
        d.syncJson();
        return d;
    }

    public static ShopData load(CompoundTag tag) {
        ShopData d = new ShopData();
        d.nextId = Math.max(1, tag.getInt("NextId"));
        d.revision = tag.getLong("Revision");
        ListTag list = tag.getList("Shops", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Shop s = Shop.load(list.getCompound(i));
            d.shops.put(s.id, s);
            d.nextId = Math.max(d.nextId, s.id + 1);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextId", nextId);
        tag.putLong("Revision", revision);
        ListTag list = new ListTag();
        for (Shop s : shops.values()) list.add(s.save());
        tag.put("Shops", list);
        return tag;
    }

    /** À appeler après toute modification : sauvegarde + rafraîchissement des écrans ouverts. */
    public void changed() {
        revision = Math.max(System.currentTimeMillis(), revision + 1);
        setDirty();
        changed = true;
        writeJson();
    }

    public boolean consumeChanged() {
        boolean c = changed;
        changed = false;
        return c;
    }

    // ---------------------------------------------------------------- JSON

    /** Au premier accès : charge le JSON (ou le crée depuis les données existantes). Ensuite : recharge s'il a été modifié. */
    private long lastJsonCheck;

    private void syncJson() {
        Path f = file();
        if (!jsonInit) {
            jsonInit = true;
            if (Files.exists(f)) {
                if (revision > fileRevision(f)) {      // shops.json plus ancien que le monde : on garde le monde
                    backupStale(f);
                    writeJson();
                    return;
                }
                if (!readJson(f)) backupBroken(f);
                else return;
            }
            writeJson();   // première fois : exporte les boutiques existantes
            return;
        }
        // get() est appelé à chaque tick : on ne regarde la date du fichier qu'une fois toutes les 2 s.
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastJsonCheck < 2000) return;
        lastJsonCheck = nowMs;
        try {
            if (Files.exists(f) && Files.getLastModifiedTime(f).toMillis() != jsonStamp) {
                if (revision > fileRevision(f)) {      // fichier remplacé par une version plus ancienne pendant que le serveur tourne
                    backupStale(f);
                    writeJson();
                } else if (readJson(f)) {
                    LOGGER.info("[Boutiques] shops.json rechargé ({} boutiques).", shops.size());
                    changed = true;   // rafraîchit les écrans ouverts
                    setDirty();
                } else {
                    jsonStamp = Files.getLastModifiedTime(f).toMillis();   // ne pas spammer ; on garde les données en mémoire
                }
            } else if (!Files.exists(f)) {
                writeJson();
            }
        } catch (IOException e) {
            LOGGER.error("[Boutiques] Lecture de shops.json impossible", e);
        }
    }

    private boolean readJson(Path f) {
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) throw new IllegalStateException("fichier vide");
            Map<Integer, Shop> loaded = new TreeMap<>();
            int max = 0;
            JsonArray arr = root.has("shops") ? root.getAsJsonArray("shops") : new JsonArray();
            for (JsonElement el : arr) {
                Shop s = Shop.fromJson(el.getAsJsonObject());
                if (loaded.containsKey(s.id)) {
                    LOGGER.warn("[Boutiques] Id de boutique en double ignoré : #{}", s.id);
                    continue;
                }
                loaded.put(s.id, s);
                max = Math.max(max, s.id);
            }
            shops.clear();
            shops.putAll(loaded);
            nextId = Math.max(Math.max(1, root.has("nextId") ? root.get("nextId").getAsInt() : 1), max + 1);
            if (root.has("revision")) revision = Math.max(revision, root.get("revision").getAsLong());
            jsonStamp = Files.getLastModifiedTime(f).toMillis();
            return true;
        } catch (Exception e) {
            LOGGER.error("[Boutiques] shops.json invalide, les boutiques en mémoire sont conservées : {}", e.toString());
            return false;
        }
    }

    private void writeJson() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_info", "Prix en centimes (1500 = 15,00 EUR). mode: SELL (la boutique vend) ou BUY (la boutique rachete). "
                    + "policeGrade: -1 = tout le monde, 0 = commissaire, 1 = officier+, 2 = tout policier. Rechargé automatiquement. "
                    + "revision: écrite par le mod, ne pas la modifier (un fichier de revision plus ancienne que le monde est mis de côté, pas appliqué).");
            root.addProperty("nextId", nextId);
            root.addProperty("revision", revision);
            JsonArray arr = new JsonArray();
            for (Shop s : shops.values()) arr.add(s.toJson());
            root.add("shops", arr);
            Path tmp = f.resolveSibling("shops.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            jsonStamp = Files.getLastModifiedTime(f).toMillis();
        } catch (IOException e) {
            LOGGER.error("[Boutiques] Écriture de shops.json impossible", e);
        }
    }

    /** Revision écrite dans shops.json (0 si absente ou illisible). */
    private static long fileRevision(Path f) {
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            return root != null && root.has("revision") ? root.get("revision").getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Met de côté un shops.json plus ancien que les données du monde (on garde les 5 dernières copies). */
    private static void backupStale(Path f) {
        try {
            Path b = f.resolveSibling("shops.json.stale-" + System.currentTimeMillis());
            Files.copy(f, b);
            LOGGER.warn("[Boutiques] shops.json plus ancien que les données du monde (restauré par un modèle, l'hébergeur ou une sauvegarde ?) : "
                    + "les boutiques du monde sont conservées, l'ancien fichier est sauvegardé dans {}", b.getFileName());
            List<Path> old = new ArrayList<>();
            try (var ds = Files.newDirectoryStream(f.getParent(), "shops.json.stale-*")) {
                for (Path p : ds) old.add(p);
            }
            Collections.sort(old);
            for (int i = 0; i < old.size() - 5; i++) Files.deleteIfExists(old.get(i));
        } catch (IOException ignored) {
        }
    }

    private static void backupBroken(Path f) {
        try {
            Path b = f.resolveSibling("shops.json.broken-" + System.currentTimeMillis());
            Files.copy(f, b);
            LOGGER.warn("[Boutiques] shops.json illisible : copie sauvegardée dans {}", b.getFileName());
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------- API

    public Shop create(String name) {
        Shop s = new Shop(nextId++, name);
        shops.put(s.id, s);
        changed();
        return s;
    }

    public boolean delete(int id) {
        boolean r = shops.remove(id) != null;
        if (r) changed();
        return r;
    }

    @Nullable
    public Shop shop(int id) {
        return shops.get(id);
    }

    public Collection<Shop> all() {
        return Collections.unmodifiableCollection(shops.values());
    }

    public List<Integer> ids() {
        return new ArrayList<>(shops.keySet());
    }
}
