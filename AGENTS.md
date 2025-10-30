Tu es un assistant developpeur expert en Scala 3, JavaFX et SQLite. Ta mission est de maintenir et faire evoluer le projet de facturation bureau **Invoicer** en respectant strictement les contraintes ci‑dessous.

# Vision fonctionnelle
- Application desktop Scala 3.3.6 + JavaFX destinee a la gestion de clients, articles/prestations et factures.
- CRUD complet pour clients et articles, recherche instantanee depuis la base SQLite locale (`invoicer.db` dans le dossier utilisateur/app).
- Creation et edition de factures avec numero libre, date, client, lignes dynamiques, calcul automatique HT/TVA/TTC et export PDF via Apache PDFBox.
- La creation de facture propose un numero sequentiel au format `FAC-AAAA-XXXX` calcule automatiquement tout en restant modifiable.
- Onglet Parametres permettant de modifier le taux de TVA (par defaut 20 %) et les coordonnees de l'entreprise emettrice (Nom, Adresse, Email, Telephone, SIRET).
- Interface en francais, i18n minimale.

# Modele de donnees (SQLite)
- `company(id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, address TEXT, email TEXT, phone TEXT, siret TEXT)`
- `clients(id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, address TEXT, email TEXT, phone TEXT, siret TEXT)`
- `items(id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, unit_price_ht REAL NOT NULL, description TEXT)`
- `invoices(id INTEGER PK AUTOINCREMENT, number TEXT NOT NULL UNIQUE, date TEXT NOT NULL, client_id INTEGER NOT NULL, vat_rate REAL NOT NULL)`
- `invoice_lines(id INTEGER PK AUTOINCREMENT, invoice_id INTEGER NOT NULL, item_id INTEGER, description TEXT, quantity REAL NOT NULL, unit_price_ht REAL NOT NULL)`
- `settings(key TEXT PRIMARY KEY, value TEXT)` initialise avec `vat_rate = 0.20`.

# Lignes directrices UI/UX
- Fenetre principale composee d'un `TabPane` (Clients, Articles, Factures, Parametres).
- Theme pastel moderne defini dans `src/main/resources/styles/app.css` : effets de cartes, boutons primaires/secondaires, tables arrondies. Toute nouvelle vue doit reutiliser les classes existantes (`card`, `form-card`, `primary-button`, etc.) pour conserver la coherence.
- L'onglet Factures reste encapsule dans un `ScrollPane` afin de garder les actions accessibles sur les petits ecrans. Le libelle `editModeLabel` doit toujours refleter le mode courant (creation ou edition).

# Export PDF
- Generer un A4 via `PdfService` (Apache PDFBox). Chaque fichier doit s'appeler `FACTURE_<numero>.pdf` et se trouver dans `~/app/factures`.
- Conserver l'entete entreprise, le titre FACTURE + numero/date, les coordonnees client, le tableau (Description | Qte | PU HT | Total HT) et le recapitulatif final (Sous-total HT, TVA xx %, Total TTC).
- La generation gere la pagination multi-page et affiche toutes les coordonnees disponibles (adresse, email, telephone, SIRET) pour l'entreprise et le client ; afficher `-` si une information manque.
- `PdfService` initialise un cache `pdfbox-font-cache` et force les logs PDFBox au niveau ERROR via `slf4j-simple`. Ne jamais retirer cette initialisation.

# Contraintes techniques
- Stack : sbt, Scala 3.3.6, JavaFX (FXML facultatif), JDBC pur (pas de frameworks lourds).
- Dependances clefs : `org.xerial:sqlite-jdbc`, `org.apache.pdfbox:pdfbox`, `org.slf4j:slf4j-simple`.
- Architecture : models + DAO + services + vues JavaFX (pattern MVC/MVVM). Code sobre, commenter uniquement les sections complexes.

# Notes de maintenance (octobre 2025)
- Executer `sbt compile` avant `sbt run` sur une nouvelle machine pour laisser PDFBox preparer son cache.
- Les coordonnees entreprise et le taux de TVA doivent etre charges depuis la base au demarrage et refléter toute mise a jour en temps reel dans l'onglet Factures.
- Les listes (clients, articles) doivent etre rafraichies lorsqu'un onglet critique devient actif (ex : `InvoicesTab.reloadReferences()`).
- Toute evolution d'UI doit respecter la palette pastel (voir `app.css`). Ajouter une classe CSS seulement si indispensable et documenter la nouveaute dans le README.
- Le service PDF repose sur les helpers `optionalLines` et `writeLine` pour assurer formatage multi-ligne et pagination ; conserver ces utilitaires lors des evolutions.
