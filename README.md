# Invoicer - Application de facturation Scala 3 / JavaFX

Invoicer est une application desktop de gestion de facturation ecrite en Scala 3.3.6 et JavaFX. Elle couvre la gestion des clients et des articles, la creation/edition de factures avec calculs automatiques (HT, TVA, TTC) et l'export PDF via Apache PDFBox. Toutes les donnees sont stockees localement dans SQLite.

## Fonctionnalites principales
- Gestion CRUD des clients et des articles, avec champ de recherche instantanee.
- Creation et edition de factures dans l'onglet Factures (numero libre, date, client, lignes dynamiques via bouton `Add`).
- Calcul automatique des totaux HT, TVA et TTC sur la base du taux en vigueur (20 % par defaut).
- Parametrage des coordonnees de l'entreprise emettrice et du taux de TVA dans l'onglet Parametres.
- Export d'une facture selectionnee en PDF A4 depuis l'onglet Factures (nommage `FACTURE_<numero>.pdf`).
- Interface utilisateur en francais, habillee d'un theme pastel moderne (voir `src/main/resources/styles/app.css`).

## Structure du projet
- `build.sbt`, `project/` : configuration sbt.
- `src/main/scala/invoicer` : code Scala (models, DAO, services, vues JavaFX).
- `src/main/resources/styles/app.css` : theme pastel commun (cartes, boutons, tableaux).
- `src/main/resources/i18n` : base pour les ressources de traduction supplementaires.

## Prerequis
- JDK 17 (ou plus recent) dans le PATH.
- [sbt](https://www.scala-sbt.org/) 1.10.x.
- Acces reseau lors du tout premier lancement pour telecharger JavaFX, SQLite JDBC et PDFBox.

## Installation et execution
```bash
sbt compile   # laisse PDFBox initialiser son cache de polices
sbt run
```
Au lancement, la fenetre principale affiche quatre onglets : Clients, Articles, Factures et Parametres. Les listes se rafraichissent automatiquement lors des operations CRUD et lorsque l'onglet Factures devient actif.

## Export PDF
- Les PDF sont enregistres dans `%USERPROFILE%\app\factures` sous Windows et `~/app/factures` sur Linux/macOS.
- Le contenu suit la structure imposee : entete entreprise, titre FACTURE + numero/date, coordonnees client, tableau (Description | Qte | PU HT | Total HT), recapitulatif (Sous-total HT, TVA xx %, Total TTC).
- Le service `PdfService` cree un cache local `pdfbox-font-cache` et force les logs PDFBox au niveau ERROR via `slf4j-simple`. Merci de conserver cette initialisation.

## Notes techniques
- Base SQLite : fichier `invoicer.db` cree automatiquement dans `%USERPROFILE%\app` (ou `~/app`).
- Schemas et migrations sont assures par les DAO au demarrage (tables `company`, `clients`, `items`, `invoices`, `invoice_lines`, `settings`).
- L'UI applique une architecture MVC/MVVM simple (services -> vues JavaFX). Les nouvelles vues doivent reutiliser les classes CSS existantes (`card`, `form-card`, `primary-button`, etc.) pour rester coherentes.
- L'onglet Factures encapsule le formulaire dans un `ScrollPane` afin de conserver l'accessibilite sur des ecrans de petite hauteur.

## Personnalisation du theme
La palette pastel et les composants stylises sont definis dans `styles/app.css`. En cas d'ajout de nouvelles classes, documenter le changement ici pour guider les contributeurs.

## Tests
Le projet ne comporte pas encore de suite de tests automatisee. Une validation manuelle via l'interface graphique est recommande apres toute modification importante.
