# Astralis 2.0 — conquête spatiale réaliste (Forge 1.19.2)

De la Terre à la Lune en fusée : on extrait du titane, on construit une fusée et une combinaison étanche,
on décolle, on se met en orbite, on traverse le vide jusqu'à la Lune et on s'y pose. Le ciel est celui du
vrai espace : Voie lactée, étoiles réelles, planètes et phases de la Lune **à la date du jour**.

## 1. Obtenir le .jar

Ce projet est livré en **code source** : il n'a pas pu être compilé là où il a été écrit (pas d'accès à Forge).
Deux solutions pour obtenir le `.jar` :

### A. Sans rien installer (GitHub Actions, gratuit)
1. Créez un compte sur github.com, puis un dépôt vide (bouton « New repository »).
2. Envoyez-y **le contenu** du dossier `astralis` (bouton « Add file » > « Upload files » ; glissez tous les fichiers
   et dossiers, y compris `.github`). Si le dossier `.github` n'est pas envoyé par glisser-déposer, créez le fichier
   `.github/workflows/build.yml` à la main et collez-y son contenu.
3. Onglet **Actions** : la compilation démarre toute seule (3 à 5 minutes). Ouvrez l'exécution terminée, puis
   téléchargez **astralis-jar** en bas de page : le `.jar` est dedans.

### B. Sur votre PC (JDK 17)
Téléchargez le MDK Forge 1.19.2 (files.minecraftforge.net), remplacez son dossier `src/` par celui d'ici, copiez
`build.gradle`, `settings.gradle`, `gradle.properties`, puis `gradlew build` (jar dans `build/libs/`).
Les fichiers visent Forge `1.19.2-43.2.0` ; si cette version n'existe pas, mettez une autre 43.x dans `build.gradle`.

Installation : Forge 1.19.2, `.jar` dans le dossier `mods`. Pas de resource pack de shaders (Optifine/Iris)
pendant les tests : ils remplacent le rendu du ciel.

## 2. Progression

1. **Titane** : minerai en sous-sol (pioche en fer). Fondre -> lingot ; 2 lingots -> 3 plaques.
2. **Circuit électronique** : cuivre + redstone + fer.
3. **Bouteille d'oxygène** : plaque + bouteille en verre + 2 algues (les bioréacteurs à algues existent vraiment).
4. **Combinaison spatiale** (4 pièces). Le plastron est le réservoir d'O2 (5 min) ; clic droit avec une bouteille pour recharger.
5. **Carburant de fusée** : 3 charbons + bouteille d'oxygène + poudre à canon (kérosène + oxygène liquide).
6. **Plateforme de lancement** : plaque, circuit, fer, bloc de redstone, béton gris.
7. **Fusée** : circuit, 4 plaques, bloc de titane, haut fourneau (le moteur).

## 3. Piloter la fusée

| Action | Commande |
|---|---|
| Poser la fusée | Clic droit sur une plateforme de lancement (à ciel ouvert) |
| Ravitailler | Clic droit sur la fusée avec du carburant (+100 par bidon, réservoir 1000) |
| Embarquer | Clic droit sur la fusée, combinaison complète exigée |
| Lancer (compte à rebours 10 s) | **Z** (avant) une fois à bord |
| Annuler le compte à rebours | **S** |
| En orbite : transfert vers l'autre astre | **Z** |
| En orbite : descente / atterrissage | **S** |
| Reprendre la fusée | Frapper la fusée au sol, vide : l'objet conserve le carburant |

Profil de vol :
- **Décollage Terre** (150 de carburant) : poussée verticale, séparation des étages, le ciel noircit et les étoiles
  apparaissent avec l'altitude, mise en orbite à 300 blocs.
- **Orbite** (dimension « Orbite ») : apesanteur, Terre sphérique sous vos pieds (nuages, lumières des villes côté nuit,
  rebord atmosphérique), Lune et Soleil. Vous pouvez sortir de la fusée (sortie extravéhiculaire) : Z/S vous propulsent
  dans la direction du regard ; votre oxygène se consomme.
- **Transfert** (200 de carburant, environ 60 s) : la Terre rapetisse derrière vous, la Lune grossit devant, puis la
  fusée se retourne pour le freinage d'insertion en orbite lunaire.
- **Alunissage** (100 de carburant) : descente propulsée jusqu'à une plateforme créée automatiquement.
- **Retour** : décollage lunaire (60), transfert (200), rentrée atmosphérique (aérofreinage, 0). Un aller-retour complet
  tient dans un réservoir plein (1000). Le carburant est vérifié avant chaque manœuvre.
- Un atterrissage trop rapide (panne sèche) détruit la fusée.

## 4. Réalisme

- **Ciel** : cubemap 6 x 1024 px avec ~70 étoiles réelles à leurs vraies coordonnées (Orion, Grande Ourse, Croix du Sud…),
  Voie lactée orientée comme la vraie (centre galactique, bandes de poussière), Soleil avec halo.
- **Éphémérides réelles** : Soleil, Lune (phase et position), Mercure, Vénus, Mars, Jupiter, Saturne selon la date de
  votre ordinateur (éléments képlériens JPL). Vérifié sur J2000 : Soleil 280,4°, Vénus 241,6°, Mars 328°, Jupiter 25,4°,
  Saturne 40,2°, Lune 223,3°.
- **Terre** : globe éclairé par le Soleil (phases, croissant, crépuscule), inclinée de 23,4°, orientation réelle à l'heure
  courante, qui tourne en accéléré. Depuis la Lune elle est presque immobile (rotation synchrone) et sa hauteur dépend
  de votre position ; elle disparaît sous l'horizon de la face cachée.
- **Lune** : phases selon l'éclairage solaire, terminateur net (pas d'atmosphère), face visible orientée vers la Terre.
- **Physique** : gravité 1/6 g, apesanteur en orbite, vide (hypoxie puis décompression), pas de feu sans oxygène,
  eau qui s'évapore, silence (sons coupés au-delà de 6 blocs), boussole affolée, cabine de fusée pressurisée.

## 5. Limites à connaître

- **Les textures sont générées par programme, pas des photographies.** Terre, Lune et ciel sont reconnaissables
  (continents, mers lunaires, étoiles réelles), mais ce n'est pas du photoréalisme au sens strict. Pour des
  images réelles libres de droits, remplacez ces fichiers dans `assets/astralis/textures/environment/` :
  - `earth.png` (équirectangulaire, longitude -180 à 180 : NASA Blue Marble),
  - `earth_night.png` (NASA Black Marble),
  - `moon.png` (équirectangulaire, longitude 0 au centre : NASA LRO Moon Kit),
  - ces trois-là sont compatibles directement.
  Le cubemap `sky_0..5.png` est en repère écliptique : une carte NASA (équatoriale) devrait d'abord être reprojetée.
- Le jour lunaire dure 20 minutes (cycle du jeu), pas 29,5 jours ; la rotation de la Terre est accélérée.
- Les distances sont comprimées (transfert de 60 s, pas 3 jours).
- Sur un serveur dédié, mettez `allow-flight=true` (sinon la sortie en apesanteur peut être sanctionnée).
- Le code n'a pas été exécuté dans Minecraft : si une erreur de compilation ou un plantage apparaît, envoyez
  la trace (`logs/latest.log` ou le message de Gradle) et le fichier fautif se corrige en quelques lignes.

## 6. Structure

- `entity/RocketEntity` : machine à états (au sol, compte à rebours, montée, orbite, transfert, descente), carburant, transfert de dimension.
- `client/RocketModel`, `RocketRenderer` : modèle 3D (≈ 9,5 blocs), flamme animée.
- `client/sky/SkyRenderer`, `Astronomy` : ciel, astres, éphémérides.
- `space/SpaceEvents` : gravité, oxygène, vide, feu, eau, sécurité du vol.
- `data/astralis/` : dimensions Lune et Orbite, minerais, recettes, loot tables, tags.
