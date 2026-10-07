package com.minenorth_shops;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Pont optionnel vers MineNorth Permis (minenorth_permis), par réflexion : aucune dépendance de compilation,
 * le mod boutiques fonctionne sans le mod permis. Thread serveur uniquement.
 *
 * Sécurité : si une boutique exige une licence et que le mod permis est absent (ou en erreur),
 * l'achat est REFUSÉ (on ne vend pas d'armes à tout le monde à cause d'un jar manquant).
 */
public final class PermisCompat {
    public static final String MODID = "minenorth_permis";
    private static final Logger LOG = LogUtils.getLogger();

    /** Licence connue du mod permis : id technique + nom affiché. */
    public record LicenceInfo(String id, String name) {}

    private static boolean init, ok;
    private static Method hasLicence, configGet;

    private PermisCompat() {}

    public static boolean loaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    private static boolean ready() {
        if (init) return ok;
        init = true;
        if (!loaded()) return ok = false;
        try {
            Class<?> api = Class.forName("com.minenorth_permis.PermisApi");
            hasLicence = api.getMethod("hasLicence", ServerPlayer.class, String.class);
            Class<?> cfg = Class.forName("com.minenorth_permis.PermisConfig");
            configGet = cfg.getMethod("get");
            ok = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.error("[Boutiques] minenorth_permis présent mais API introuvable : licences refusées par défaut.", e);
            ok = false;
        }
        return ok;
    }

    /** Le mod permis est-il utilisable ? */
    public static boolean available() {
        return ready();
    }

    /** true si la boutique n'exige rien, ou si le joueur a la licence valide (non expirée). MineNorth API. */
    public static boolean has(ServerPlayer p, String licence) {
        if (licence == null || licence.isBlank()) return true;
        return fr.minenorth.api.MineNorth.licences().has(p.server, p.getUUID(), licence);
    }

    /** Licences déclarées dans la config du mod permis (vide si absent). */
    public static List<LicenceInfo> licences() {
        List<LicenceInfo> out = new ArrayList<>();
        if (!ready()) return out;
        try {
            Object root = configGet.invoke(null);
            Field f = root.getClass().getField("licences");
            for (Object l : (List<?>) f.get(root)) {
                Object id = l.getClass().getField("id").get(l);
                Object name = l.getClass().getField("name").get(l);
                if (id == null || id.toString().isBlank()) continue;
                out.add(new LicenceInfo(id.toString(), name == null || name.toString().isBlank() ? id.toString() : name.toString()));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[Boutiques] Impossible de lire la liste des licences de minenorth_permis.", e);
        }
        return out;
    }

    /** Nom lisible d'une licence (ex. « Port d'armes »), ou l'id si inconnue. */
    public static String name(String licence) {
        if (licence == null || licence.isBlank()) return "";
        for (LicenceInfo l : licences()) if (l.id().equals(licence)) return l.name();
        return licence;
    }

    /** Message de refus, ou null si le joueur peut commercer ici. */
    public static String denial(ServerPlayer p, String licence) {
        if (has(p, licence)) return null;
        if (!available()) return "Boutique indisponible : licence « " + licence + " » requise mais le système de permis est absent.";
        return "Il vous faut une licence valide pour commercer ici : " + name(licence) + ".";
    }
}
