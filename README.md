# Boutiques MineNorthRP (`minenorth_shops`)

Mod Forge 1.20.1 : boutiques créées et configurées **en jeu via une interface** (même style que MineNorth Bank),
en **vente** ou en **rachat**, assignables à **n'importe quelle entité**. Paiement en espèces ou par carte via
`minenorth_eurobank` (dépendance obligatoire).

## Compilation

1. Compiler EuroBank (`./gradlew build` dans `systemebanqueminenorth`).
2. Copier `build/libs/minenorth_eurobank-1.0.0.jar` dans `libs/` de ce projet.
   (Autre version : changer `eurobank_version` dans `gradle.properties`.)
3. `./gradlew build` → `build/libs/minenorth_shops-1.1.0.jar`.
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
| Licence requise | Bouton **Licence requise : …** : chaque clic passe à la licence suivante (liste lue dans la config de `minenorth_permis`), **Aucune** = retirer. Voir ci-dessous |
| Rubrique d'un article | Champ **Rubrique** à côté de l'objet, lors de **Ajouter** ou **Appliquer** (vide = sans rubrique). Dans `shops.json` : `"category": "Armes"` sur l'article. Les joueurs voient des onglets (Tous, une par rubrique, Autres) |
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

### Licence requise (mod MineNorth Permis)

Une boutique peut exiger une licence de `minenorth_permis` (ex. port d'armes pour l'armurerie). Sans licence valide
(possédée et non expirée), le joueur peut **consulter** la boutique mais ne peut ni acheter ni vendre : les boutons sont
grisés, la licence s'affiche en rouge dans l'en-tête, et le serveur refuse toute transaction.

- Dans le panneau : bouton **Licence requise** de la boutique.
- En commande : `/shops licence <id> <licence>` (autocomplétion des licences), `/shops licence <id> aucune` pour retirer.
- La licence s'applique à toute la boutique, en vente comme en rachat.

`minenorth_permis` est une dépendance **optionnelle** (version 1.3.0 ou plus) : sans lui, le mod boutiques fonctionne
normalement. Par sécurité, une boutique qui exige une licence est **bloquée** si le mod permis est absent (on ne vend
pas d'armes à tout le monde à cause d'un jar manquant).

### Autres commandes

- `/shops list` — liste des boutiques.
- `/shops edit <id>` — ouvre l'édition.
- `/shops licence <id> <licence|aucune>` — licence exigée pour commercer.
- `/shops police <id> <tous|policier|officier|commissaire>` — boutique réservée à la police.
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

Toutes les boutiques sont dans **`config/minenorth_shops/shops.json`** (un seul fichier, modifiable à la main).
Il est créé au premier lancement à partir des boutiques existantes (`world/data/minenorth_shops.dat`), réécrit à chaque
modification en jeu et **rechargé automatiquement** quand tu le modifies (pas de redémarrage). Si le JSON est invalide,
les boutiques en mémoire sont conservées et une copie `shops.json.broken-…` est faite.

```json
{ "nextId": 2, "shops": [ {
  "id": 1, "name": "Armurerie", "mode": "SELL", "allowCash": true, "allowCard": true,
  "licence": "port_armes", "policeGrade": -1,
  "entries": [ { "id": 1, "item": "minecraft:iron_sword", "nbt": "{Damage:0}", "quantity": 1, "price": 1500 } ]
} ] }
```
Prix en centimes (1500 = 15,00 €). `nbt` est optionnel (SNBT). Un article dont l'objet est inconnu est ignoré (log).

## Notes techniques

- Tout est validé côté serveur : session liée à l'entité (présente, toujours liée, à portée), prix attendu renvoyé
  par le client (refus si un admin a changé le prix entre-temps), permission op revérifiée à chaque action admin.
- L'id de la boutique est stocké dans les données persistantes de l'entité (`minenorth_shops:shop`).
- En rachat, le versement sur compte utilise `BankApi.refund`. Un alias `BankApi.deposit(p, cents)` dans EuroBank
  serait plus lisible.

### Boutique réservée à la police (mod MineNorth Police)

Une boutique peut être réservée aux policiers enregistrés (`/police grade`), avec un grade minimum :

| Réglage | Qui peut l'ouvrir et acheter |
|---|---|
| Police : non | tout le monde (défaut) |
| Police : oui | tout policier (Sous-officier et +) |
| Police : off.+ | Officier et Commissaire |
| Police : comm. | Commissaire uniquement |

- Dans le panneau : bouton **Police : …** de la boutique (chaque clic passe au réglage suivant).
- En commande : `/shops police <id> <tous|policier|officier|commissaire>`.
- Un non-policier qui clique sur le PNJ reçoit « Cette boutique est réservée à la police. » et rien ne s'ouvre.
  Les ops peuvent l'ouvrir pour la consulter, mais pas y acheter sans grade.
- Cumulable avec la licence et le choix espèces / carte.
- Par sécurité, une boutique police est **bloquée** si le mod Police est absent.

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).
