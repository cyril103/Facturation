Tu es un assistant développeur expert en Scala 3 et JavaFX. Génère un projet complet et exécutable de logiciel de facturation respectant STRICTEMENT les spécifications suivantes. Fournis tout le code, les fichiers de build, et un README clair.

# Objectif
Créer une application de facturation bureau (desktop) en Scala 3.3.6 + JavaFX avec :
- Gestion des clients (CRUD)
- Gestion des articles/prestations (CRUD) avec prix HT
- Création de factures : choisir client, date, numéro de facture, ajouter des lignes au fil de l’eau via bouton « Add »
- TVA par défaut 20 %, modifiable dans un écran « Paramètres »
- Informations de l’entreprise émettrice (Nom, Adresse, Email, Téléphone, SIRET) paramétrables dans l’onglet « Paramètres »
- Recherche rapide clients/articles depuis la base
- Persistance locale en SQLite (fichier `invoicer.db` dans dossier utilisateur/app)
- Export de la facture en PDF (mise en page simple et propre) via Apache PDFBox
- Calculs automatiques : Sous-total HT, TVA, Total TTC
- Internationalisation fr minimale (libellés en français)

# Schéma SQLite
Tables et colonnes :
- company(id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, address TEXT, email TEXT, phone TEXT, siret TEXT)
- clients(id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, address TEXT, email TEXT, phone TEXT, siret TEXT)
- items(id INTEGER PK AUTOINCREMENT, name TEXT NOT NULL, unit_price_ht REAL NOT NULL, description TEXT)
- invoices(id INTEGER PK AUTOINCREMENT, number TEXT NOT NULL UNIQUE, date TEXT NOT NULL, client_id INTEGER NOT NULL, vat_rate REAL NOT NULL)
- invoice_lines(id INTEGER PK AUTOINCREMENT, invoice_id INTEGER NOT NULL, item_id INTEGER, description TEXT, quantity REAL NOT NULL, unit_price_ht REAL NOT NULL)
- settings(key TEXT PRIMARY KEY, value TEXT) — stocke `vat_rate` = `0.20` par défaut

# UI/UX (JavaFX)
- Fenêtre principale : `TabPane` avec 4 onglets :
  1. « Clients » : gestion CRUD
  2. « Articles » : gestion CRUD
  3. « Factures » : édition et export
  4. « Paramètres » :
     - TVA (%)
     - Coordonnées de l’entreprise (Nom, Adresse, Email, Téléphone, SIRET)

# Export PDF
- Utilise Apache PDFBox
- Génère un PDF A4 avec :
  - En-tête : coordonnées de l’entreprise émettrice (Nom, Adresse, Email, Téléphone, SIRET)
  - Titre « FACTURE » + numéro + date
  - Coordonnées client
  - Tableau lignes : Description | Qté | PU HT | Total HT
  - Pied : Sous-total HT, TVA (xx %), Total TTC
  - Nom de fichier : `FACTURE_<numero>.pdf`

# Contraintes techniques
- Scala 3.3.6
- sbt
- JavaFX (controls, FXML facultatif)
- SQLite via `org.xerial:sqlite-jdbc`
- PDF via `org.apache.pdfbox:pdfbox`
- Structure MVC/MVVM simple (models + DAO + services + vues JavaFX)
- Pas de frameworks lourds. JDBC direct + DAO.
- Code commenté et lisible.

# Notes de maintenance (oct. 2025)
- L’onglet **Factures** permet la création et l’édition depuis un même formulaire ; le bouton « Éditer facture » charge la facture sélectionnée et le libellé en haut du formulaire indique le mode actif.
- Le formulaire de facture est encapsulé dans un `ScrollPane` pour garantir l’accès aux boutons sur les écrans de petite hauteur.
- `PdfService` enregistre les PDF dans `~/app/factures` et crée un cache local `pdfbox-font-cache` afin d’éviter la reconstruction longue du cache de polices à chaque export.
- Les logs PDFBox sont forcement limités au niveau `ERROR` et `slf4j-simple` est utilisé pour supprimer l’avertissement « StaticLoggerBinder ».
- Toute modification future du service PDF doit préserver cette initialisation (cache + configuration SLF4J), sinon le premier export peut devenir très lent et bruyant.
- Pour lancer l’application dans de bonnes conditions, exécuter `sbt compile` avant `sbt run` lors d’une nouvelle installation afin de laisser PDFBox créer son cache si nécessaire.
