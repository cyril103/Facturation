# Invoicer – Application de facturation JavaFX / Scala 3

Application desktop de gestion de facturation écrite en Scala 3.3.6 et JavaFX. Elle couvre la gestion des clients, des articles, la création de factures avec calculs automatiques et l’export PDF via Apache PDFBox. Les données sont stockées localement dans une base SQLite.

## Fonctionnalités principales
- CRUD complet sur les clients et les articles avec recherche rapide.
- Création de factures : choix du client, date, numéro libre, ajout de lignes via bouton « Ajouter ».
- Calcul automatique des montants (sous-total HT, TVA, total TTC) selon un taux paramétrable (20 % par défaut).
- Paramétrage des coordonnées de l’entreprise émettrice et du taux de TVA dans l’onglet « Paramètres ».
- Export d’une facture sélectionnée en PDF (format A4) avec mise en page simple : en-tête entreprise, coordonnées client, tableau des lignes et récapitulatif des totaux.
- Internationalisation minimale en français (libellés UI en français).

## Structure du projet
- `src/main/scala/invoicer` : code Scala (base de données, modèles, services, interface JavaFX).
- `src/main/resources` : ressources (emplacement dédié aux fichiers i18n supplémentaires si besoin).
- `invoicer.db` : base SQLite créée automatiquement dans `%USERPROFILE%\app\` (ou `$HOME/app/` sous Linux/macOS).

## Prérequis
- JDK 17 ou supérieur installé.
- [sbt](https://www.scala-sbt.org/) 1.10.2 ou supérieur (voir `project/build.properties`).
- Accès internet lors du premier `sbt run` pour télécharger les dépendances (JavaFX, SQLite JDBC, PDFBox).

## Installation et exécution
```bash
sbt run
```
Le téléchargement des dépendances peut prendre quelques instants au premier lancement. L’application démarre ensuite avec une fenêtre contenant quatre onglets : Clients, Articles, Factures et Paramètres.

## Export PDF
Les fichiers PDF sont générés dans `%USERPROFILE%\app\` (ou `$HOME/app/`). Le nom du fichier suit la convention `FACTURE_<numero>.pdf`. Les coordonnées de l’entreprise et le taux de TVA utilisés proviennent des paramètres enregistrés.

## Notes complémentaires
- Le schéma SQLite est appliqué automatiquement au démarrage (tables `company`, `clients`, `items`, `invoices`, `invoice_lines`, `settings`).
- Le taux de TVA est stocké en base dans la table `settings` (clé `vat_rate`) et appliqué par défaut lors de la création de nouvelles factures.
- Les dépendances JavaFX incluent les classifieurs spécifiques à l’OS pour assurer la présence des bibliothèques natives.

## Lancer les tests
Ce projet ne contient pas encore de suite de tests automatisés. Vous pouvez valider manuellement les fonctionnalités principales via l’interface graphique après compilation.
