# Boutiques MineNorthRP (`minenorth_shops`)

Mod Forge 1.20.1 : boutiques créées et configurées **en jeu via une interface** (même style que MineNorth Bank),
en **vente** ou en **rachat**, assignables à **n'importe quelle entité**. Paiement en espèces ou par carte via
`minenorth_eurobank` (dépendance obligatoire).

## Compilation

1. Compiler EuroBank (`./gradlew build` dans `systemebanqueminenorth`).
2. Copier `build/libs/minenorth_eurobank-1.0.0.jar` dans `libs/` de ce projet.
   (Autre version : changer `eurobank_version` dans `gradle.properties`.)
3. `./gradlew build` → `build/libs/minenorth_shops-1.0.0.jar`.
4. Sur le serveur : mettre les **deux** jars dans `mods/` (côté client aussi, il y a des écrans).

## Utilisation (admins / op niveau 2)

| Action | Comment |
|---|---|
| Ouvrir le panneau | `/shops` |
| Créer une boutique | Taper un nom → **Créer** (on arrive directement dans l'édition) |
| Vente / Rachat | Bouton de mode : *Vend aux joueurs* ↔ *Achète aux joueurs* |
| Moyens de paiement | **Espèces : oui/non**, **Carte : oui/non** (en rachat : *Compte* = argent versé sur le compte) |
| Ajouter un article | **Choisir dans l'inventaire** : votre inventaire s'affiche, cliquez l'objet (par défaut : l'objet en main). NBT conservé (enchantements, nom, données de mods). La quantité est préremplie avec la taille du tas. Mettre le prix du lot → **Ajouter** |
| Modifier un article | Cliquer la ligne → changer prix/quantité → **Appliquer**. **Monter** = réordonner, **Retirer** = supprimer |
| Tester | **Aperçu** ouvre la boutique comme un joueur |

### Assigner une boutique à une entité

N'importe quelle entité : villageois, armor stand, PNJ d'un autre mod, cadre, animal…

- **Outil de liaison** (bouton dans le panneau, ou `/shops linker <id>`) : clic droit sur l'entité = assigner,
  accroupi + clic droit = retirer.
- **Commande** : `/shops assign @e[type=villager,limit=1,sort=nearest] <id>` — `/shops unassign <entités>`.
- `/shops info <entité>` : quelle boutique est liée.
- Admin accroupi + clic droit (main vide) sur un vendeur : ouvre directement l'édition de sa boutique.

Par défaut, l'entité devient invulnérable, immobile (IA coupée) et ne despawn plus. Retirer la boutique restaure ces
réglages. Le tout est configurable.

### Autres commandes

- `/shops list` — liste des boutiques.
- `/shops edit <id>` — ouvre l'édition.
- `/shops open <id> [joueurs]` — ouvre une boutique sans entité (blocs de commande, PNJ d'autres mods qui exécutent
  des commandes…).

## Côté joueur

Clic droit sur le vendeur → écran de boutique : espèces et solde affichés, liste des articles (survol de l'icône =
infobulle), choix du nombre de lots (− / + / Max), puis **Payer en espèces** / **Payer par carte**
(ou **Vendre (espèces)** / **Vendre (compte)** en rachat). La monnaie est rendue automatiquement.

Règles de paiement (celles de l'API EuroBank) : par carte, il faut un compte, **sa propre carte** dans l'inventaire et un
solde suffisant. En rachat, les outils abîmés ne sont pas repris ; si l'article a du NBT, l'objet doit être identique.

## Configuration

`world/serverconfig/minenorth_shops-server.toml`

```toml
[entites]
    protegerEntites = true   # invulnérable à l'assignation
    figerEntites = true      # IA coupée à l'assignation
    distanceMax = 8.0        # distance max joueur ↔ vendeur
[achats]
    lotsMax = 64             # lots max par transaction
```

Les boutiques sont sauvegardées dans `world/data/minenorth_shops.dat`.

## Notes techniques

- Tout est validé côté serveur : session liée à l'entité (présente, toujours liée, à portée), prix attendu renvoyé
  par le client (refus si un admin a changé le prix entre-temps), permission op revérifiée à chaque action admin.
- L'id de la boutique est stocké dans les données persistantes de l'entité (`minenorth_shops:shop`).
- En rachat, le versement sur compte utilise `BankApi.refund`. Un alias `BankApi.deposit(p, cents)` dans EuroBank
  serait plus lisible.
