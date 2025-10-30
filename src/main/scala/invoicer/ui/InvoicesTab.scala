package invoicer
package ui

import invoicer.model.{Client, InvoiceDetails, InvoiceLine, InvoiceTotals, Item}
import invoicer.service.{ClientService, InvoiceService, ItemService}
import invoicer.util.{DialogSupport, Formatting}

import java.time.LocalDate
import java.util.UUID
import javafx.collections.FXCollections
import javafx.geometry.{Insets, Orientation, Pos}
import javafx.scene.control._
import javafx.scene.layout.{BorderPane, GridPane, HBox, Priority, VBox}
import javafx.util.StringConverter

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/** Onglet d'édition et d'export des factures. */
final class InvoicesTab(
    clientService: ClientService,
    itemService: ItemService,
    invoiceService: InvoiceService,
    onExportRequested: InvoiceDetails => Unit,
    defaultVatRate: BigDecimal
) extends Tab:
  setText("Factures")
  closableProperty().set(false)

  private case class InvoiceListRow(invoiceId: Int, number: String, date: LocalDate, clientName: String)
  private case class InvoiceLineRow(itemId: Option[Int], description: String, quantity: BigDecimal, unitPrice: BigDecimal)

  private val invoicesData = FXCollections.observableArrayList[InvoiceListRow]()
  private val invoiceLinesData = FXCollections.observableArrayList[InvoiceLineRow]()
  private val clientsData = FXCollections.observableArrayList(clientService.list()*)
  private val itemsData = FXCollections.observableArrayList(itemService.list()*)

  private val invoicesTable = buildInvoicesTable()
  private val invoiceNumberField = new TextField(generateInvoiceNumber())
  private val invoiceDatePicker = new DatePicker(LocalDate.now())
  private val clientCombo = new ComboBox[Client](clientsData)
  private val vatRateField = new TextField((defaultVatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString())

  private val itemCombo = new ComboBox[Item](itemsData)
  private val lineDescriptionField = new TextField()
  private val lineQuantityField = new TextField("1")
  private val lineUnitPriceField = new TextField("0.00")
  private val invoiceLinesTable = buildLinesTable()

  private val subTotalLabel = new Label("Sous-total HT : 0,00 ?")
  private val vatLabel = new Label("TVA (0 %) : 0,00 ?")
  private val totalLabel = new Label("Total TTC : 0,00 ?")
  subTotalLabel.getStyleClass.add("totals-line")
  vatLabel.getStyleClass.add("totals-line")
  totalLabel.getStyleClass.add("totals-line-strong")

  private val invoiceDetailsArea = new TextArea()
  invoiceDetailsArea.setEditable(false)
  invoiceDetailsArea.setPromptText("Sélectionnez une facture pour afficher ses détails...")
  invoiceDetailsArea.getStyleClass.add("details-area")

  private val editModeLabel = new Label("Création d'une nouvelle facture")
  editModeLabel.getStyleClass.add("section-subtitle")

  private var currentDefaultVatRate = defaultVatRate
  private var currentEditingInvoiceId: Option[Int] = None

  initializeComboBoxes()
  refreshInvoices()
  setContent(buildContent())

  private def buildContent(): BorderPane =
    val root = new BorderPane()
    root.getStyleClass.addAll("card", "invoices-tab")
    root.setPadding(new Insets(24))

    val header = new VBox(8, createSectionTitle("Gestion des factures"))
    header.getStyleClass.add("section-header")
    root.setTop(header)

    val topSection = buildTopSection()

    val creationPane = buildCreationPane()
    creationPane.setFillWidth(true)

    val scrollableEditor = new ScrollPane(creationPane)
    scrollableEditor.setFitToWidth(true)
    scrollableEditor.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollableEditor.getStyleClass.add("editor-scroll")

    val splitPane = new SplitPane()
    splitPane.setOrientation(Orientation.VERTICAL)
    splitPane.getItems.addAll(topSection, scrollableEditor)
    splitPane.setDividerPositions(0.4)
    splitPane.getStyleClass.add("content-split")
    SplitPane.setResizableWithParent(topSection, true)
    SplitPane.setResizableWithParent(scrollableEditor, true)

    root.setCenter(splitPane)
    root.setBottom(buildTotalsPane())
    root

  private def buildTopSection(): VBox =
    val exportButton = new Button("Exporter en PDF")
    exportButton.setOnAction(_ => exportSelectedInvoice())
    exportButton.getStyleClass.add("primary-button")

    val editButton = new Button("Editer facture")
    editButton.setOnAction(_ => openEditorForSelectedInvoice())
    editButton.getStyleClass.add("ghost-button")

    val refreshButton = new Button("Rafraichir")
    refreshButton.setOnAction(_ => refreshInvoices())
    refreshButton.getStyleClass.add("ghost-button")

    val buttons = new HBox(10, exportButton, editButton, refreshButton)
    buttons.setAlignment(Pos.CENTER_RIGHT)
    buttons.getStyleClass.add("button-row")

    val tableTitle = createSubtitle("Factures existantes")
    val detailsLabel = createSubtitle("Details de la facture")
    val detailsArea = new VBox(14, tableTitle, invoicesTable, buttons, detailsLabel, invoiceDetailsArea)
    VBox.setVgrow(invoicesTable, Priority.ALWAYS)
    VBox.setVgrow(invoiceDetailsArea, Priority.ALWAYS)
    detailsArea.getStyleClass.add("list-card")
    detailsArea

  private def buildCreationPane(): VBox =
    val grid = new GridPane()
    grid.setHgap(10)
    grid.setVgap(10)
    grid.setPadding(new Insets(10, 0, 10, 0))
    grid.getStyleClass.add("form-grid")

    grid.add(new Label("Numéro *"), 0, 0)
    grid.add(invoiceNumberField, 1, 0)
    grid.add(new Label("Date *"), 0, 1)
    grid.add(invoiceDatePicker, 1, 1)
    grid.add(new Label("Client *"), 0, 2)
    grid.add(clientCombo, 1, 2)
    grid.add(new Label("TVA (%)"), 0, 3)
    grid.add(vatRateField, 1, 3)

    val addLineButton = new Button("Add")
    addLineButton.setOnAction(_ => addInvoiceLine())
    addLineButton.getStyleClass.add("primary-button")

    val removeLineButton = new Button("Retirer")
    removeLineButton.setOnAction(_ => removeSelectedLine())
    removeLineButton.getStyleClass.add("danger-button")

    val lineButtons = new HBox(10, addLineButton, removeLineButton)
    lineButtons.setAlignment(Pos.CENTER_LEFT)
    lineButtons.getStyleClass.add("button-row")

    val lineEditor = buildLineEditor()
    val linesTitle = createSubtitle("Lignes de facture")
    val linesBox = new VBox(12, linesTitle, lineEditor, lineButtons, invoiceLinesTable)
    VBox.setVgrow(invoiceLinesTable, Priority.ALWAYS)
    linesBox.getStyleClass.add("form-card")

    val saveButton = new Button("Enregistrer la facture")
    saveButton.setDefaultButton(true)
    saveButton.setOnAction(_ => saveInvoice())
    saveButton.getStyleClass.add("primary-button")

    val newButton = new Button("Nouvelle facture")
    newButton.setOnAction(_ => resetForm())
    newButton.getStyleClass.add("ghost-button")

    val buttons = new HBox(10, saveButton, newButton)
    buttons.setAlignment(Pos.CENTER_RIGHT)
    buttons.getStyleClass.add("button-row")

    val infoTitle = createSubtitle("Informations de la facture")
    val infoBox = new VBox(12, infoTitle, grid)
    infoBox.getStyleClass.add("form-card")

    val container = new VBox(20, editModeLabel, infoBox, linesBox, buttons)
    container.getStyleClass.add("editor-content")
    container

  private def buildLineEditor(): GridPane =
    val grid = new GridPane()
    grid.setHgap(10)
    grid.setVgap(10)
    grid.getStyleClass.add("form-grid")

    grid.add(new Label("Article"), 0, 0)
    grid.add(itemCombo, 1, 0)
    grid.add(new Label("Description *"), 0, 1)
    grid.add(lineDescriptionField, 1, 1)
    grid.add(new Label("Quantité *"), 0, 2)
    grid.add(lineQuantityField, 1, 2)
    grid.add(new Label("PU HT *"), 0, 3)
    grid.add(lineUnitPriceField, 1, 3)

    grid

  private def buildTotalsPane(): VBox =
    val box = new VBox(5, subTotalLabel, vatLabel, totalLabel)
    box.setAlignment(Pos.BASELINE_RIGHT)
    box.getStyleClass.add("totals-panel")
    box

  private def buildInvoicesTable(): TableView[InvoiceListRow] =
    val table = new TableView[InvoiceListRow]()
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS)
    table.getStyleClass.add("elevated-table")

    val numberCol = new TableColumn[InvoiceListRow, String]("Numéro")
    numberCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(cell.getValue.number)
    )

    val dateCol = new TableColumn[InvoiceListRow, String]("Date")
    dateCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(cell.getValue.date.toString)
    )

    val clientCol = new TableColumn[InvoiceListRow, String]("Client")
    clientCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(cell.getValue.clientName)
    )

    table.getColumns.addAll(numberCol, dateCol, clientCol)
    table.setItems(invoicesData)
    table.getSelectionModel.selectedItemProperty().addListener { (_, _, row) =>
      if row != null then loadInvoiceDetails(row.invoiceId)
    }
    table

  private def buildLinesTable(): TableView[InvoiceLineRow] =
    val table = new TableView[InvoiceLineRow]()
    table.setPrefHeight(280)
    table.setMinHeight(240)
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS)
    table.setItems(invoiceLinesData)
    table.getStyleClass.add("elevated-table")

    val descriptionCol = new TableColumn[InvoiceLineRow, String]("Description")
    descriptionCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(cell.getValue.description)
    )

    val quantityCol = new TableColumn[InvoiceLineRow, String]("Qté")
    quantityCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(cell.getValue.quantity.bigDecimal.stripTrailingZeros().toPlainString)
    )

    val unitPriceCol = new TableColumn[InvoiceLineRow, String]("PU HT")
    unitPriceCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(
        cell.getValue.unitPrice.bigDecimal.stripTrailingZeros().toPlainString
      )
    )

    val totalCol = new TableColumn[InvoiceLineRow, String]("Total HT")
    totalCol.setCellValueFactory(cell =>
      val total = (cell.getValue.quantity * cell.getValue.unitPrice).setScale(2, BigDecimal.RoundingMode.HALF_UP)
      javafx.beans.property.SimpleStringProperty(Formatting.formatAmount(total))
    )

    table.getColumns.addAll(descriptionCol, quantityCol, unitPriceCol, totalCol)
    table

  private def initializeComboBoxes(): Unit =
    clientCombo.setConverter(new StringConverter[Client]:
      override def toString(client: Client): String = if client == null then "" else client.name
      override def fromString(string: String): Client = clientsData.asScala.find(_.name == string).orNull
    )
    clientCombo.setCellFactory(_ =>
      new ListCell[Client]:
        override def updateItem(item: Client, empty: Boolean): Unit =
          super.updateItem(item, empty)
          setText(if empty || item == null then null else item.name)
    )

    itemCombo.setConverter(new StringConverter[Item]:
      override def toString(item: Item): String = if item == null then "" else item.name
      override def fromString(string: String): Item = itemsData.asScala.find(_.name == string).orNull
    )
    itemCombo.setCellFactory(_ =>
      new ListCell[Item]:
        override def updateItem(item: Item, empty: Boolean): Unit =
          super.updateItem(item, empty)
          setText(if empty || item == null then null else s"${item.name} (${Formatting.formatAmount(item.unitPriceHt)})")
    )
    itemCombo.valueProperty().addListener { (_, _, item) =>
      if item != null then
        lineDescriptionField.setText(item.description.getOrElse(item.name))
        lineUnitPriceField.setText(item.unitPriceHt.bigDecimal.stripTrailingZeros().toPlainString)
    }

  private def refreshInvoices(): Unit =
    val rows = invoiceService.listWithClients().collect { case (invoice, clientName) =>
      InvoiceListRow(invoice.id.getOrElse(0), invoice.number, invoice.date, clientName)
    }
    invoicesData.setAll(rows*)

  private def openEditorForSelectedInvoice(): Unit =
    Option(invoicesTable.getSelectionModel.getSelectedItem) match
      case Some(row) =>
        invoiceService.loadDetails(row.invoiceId) match
          case Some(details) => populateForm(details)
          case None =>
            DialogSupport.showError("Erreur", "Impossible de charger la facture sélectionnée pour édition.")
      case None =>
        resetForm()
        DialogSupport.showInfo("Information", "Aucune facture sélectionnée. Un nouveau formulaire est prêt pour la création.")

  private def populateForm(details: InvoiceDetails): Unit =
    currentEditingInvoiceId = details.invoice.id
    editModeLabel.setText(s"Édition de la facture ${details.invoice.number}")

    invoiceNumberField.setText(details.invoice.number)
    invoiceDatePicker.setValue(details.invoice.date)

    details.client.id match
      case Some(clientId) =>
        val maybeClient = clientsData.asScala.find(_.id.contains(clientId))
        maybeClient match
          case Some(client) => clientCombo.getSelectionModel.select(client)
          case None =>
            clientsData.add(details.client)
            clientCombo.getSelectionModel.select(details.client)
      case None =>
        clientCombo.getSelectionModel.clearSelection()

    val vatPercent = (details.invoice.vatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP)
    vatRateField.setText(vatPercent.bigDecimal.stripTrailingZeros().toPlainString)

    val lineRows = details.lines.map { line =>
      InvoiceLineRow(line.itemId, line.description, line.quantity, line.unitPriceHt)
    }
    invoiceLinesData.setAll(lineRows*)
    clearLineInputs()
    recalcTotals()

  private def selectInvoiceRow(invoiceId: Int): Unit =
    invoicesData.asScala.find(_.invoiceId == invoiceId).foreach { row =>
      invoicesTable.getSelectionModel.select(row)
      invoicesTable.scrollTo(row)
    }

  private def addInvoiceLine(): Unit =
    parseLineInput() match
      case Success(row) =>
        invoiceLinesData.add(row)
        clearLineInputs()
        recalcTotals()
      case Failure(exception) =>
        DialogSupport.showError("Erreur", exception.getMessage)

  private def parseLineInput(): Try[InvoiceLineRow] =
    Try {
      val description = lineDescriptionField.getText.trim
      if description.isEmpty then throw IllegalArgumentException("La description de ligne est obligatoire.")

      val quantity = BigDecimal(lineQuantityField.getText.replace(",", ".").trim)
      if quantity <= 0 then throw IllegalArgumentException("La quantité doit être positive.")

      val price = BigDecimal(lineUnitPriceField.getText.replace(",", ".").trim)
      if price < 0 then throw IllegalArgumentException("Le prix unitaire doit être positif.")

      InvoiceLineRow(Option(itemCombo.getValue).flatMap(_.id), description, quantity, price)
    }

  private def clearLineInputs(): Unit =
    itemCombo.getSelectionModel.clearSelection()
    lineDescriptionField.clear()
    lineQuantityField.setText("1")
    lineUnitPriceField.setText("0.00")

  private def removeSelectedLine(): Unit =
    val selected = invoiceLinesTable.getSelectionModel.getSelectedItem
    if selected != null then
      invoiceLinesData.remove(selected)
      recalcTotals()

  private def recalcTotals(): Unit =
    val lines = invoiceLinesData.asScala.toSeq.map(row =>
      InvoiceLine(
        id = None,
        invoiceId = 0,
        itemId = row.itemId,
        description = row.description,
        quantity = row.quantity,
        unitPriceHt = row.unitPrice
      )
    )
    val vatRate = parseVatRateInput().getOrElse(currentDefaultVatRate)
    val totals = invoiceService.computeTotals(lines, vatRate)
    updateTotauxLabels(totals, vatRate)

  private def updateTotauxLabels(totals: InvoiceTotals, vatRate: BigDecimal): Unit =
    subTotalLabel.setText(s"Sous-total HT : ${Formatting.formatAmount(totals.subTotal)}")

    val percent = (vatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP)
    val percentText = percent.bigDecimal.stripTrailingZeros().toPlainString
    vatLabel.setText(s"TVA (${percentText} %) : ${Formatting.formatAmount(totals.vatAmount)}")
    totalLabel.setText(s"Total TTC : ${Formatting.formatAmount(totals.totalInclTax)}")

  private def parseVatRateInput(): Try[BigDecimal] =
    val text = vatRateField.getText.replace(",", ".").trim
    if text.isEmpty then Success(currentDefaultVatRate)
    else
      Try(BigDecimal(text)).map { numeric =>
        (numeric / BigDecimal(100)).setScale(4, BigDecimal.RoundingMode.HALF_UP)
      }

  private def saveInvoice(): Unit =
    val client = clientCombo.getValue
    if client == null then
      DialogSupport.showError("Erreur", "Veuillez sélectionner un client.")
      return

    val number = invoiceNumberField.getText.trim
    if number.isEmpty then
      DialogSupport.showError("Erreur", "Le numéro de facture est obligatoire.")
      return

    if invoiceLinesData.isEmpty then
      DialogSupport.showError("Erreur", "Ajoutez au moins une ligne avant d'enregistrer.")
      return

    parseVatRateInput() match
      case Failure(_) =>
        DialogSupport.showError("Erreur", "Le taux de TVA est invalide.")
      case Success(vatRate) =>
        val lines = invoiceLinesData.asScala.toSeq.map { row =>
          InvoiceLine(
            id = None,
            invoiceId = currentEditingInvoiceId.getOrElse(0),
            itemId = row.itemId,
            description = row.description,
            quantity = row.quantity,
            unitPriceHt = row.unitPrice
          )
        }

        val date = Option(invoiceDatePicker.getValue).getOrElse(LocalDate.now())
        val clientId = client.id.getOrElse(throw IllegalStateException("Client invalide"))
        val wasEditing = currentEditingInvoiceId.isDefined

        val result: Try[InvoiceDetails] = currentEditingInvoiceId match
          case Some(invoiceId) =>
            val linesWithId = lines.map(_.copy(invoiceId = invoiceId))
            Try(invoiceService.update(invoiceId, number, date, clientId, vatRate, linesWithId))
          case None =>
            Try(invoiceService.create(number, date, clientId, vatRate, lines))

        result match
          case Success(details) =>
            val message =
              if wasEditing then s"Facture ${details.invoice.number} mise à jour."
              else s"Facture ${details.invoice.number} enregistrée."
            DialogSupport.showInfo("Succès", message)
            refreshInvoices()
            details.invoice.id.foreach(selectInvoiceRow)
            resetForm()
            details.invoice.id.foreach(loadInvoiceDetails)
          case Failure(exception) =>
            DialogSupport.showError("Erreur", exception.getMessage)

  private def resetForm(): Unit =
    currentEditingInvoiceId = None
    editModeLabel.setText("Création d'une nouvelle facture")
    invoiceNumberField.setText(generateInvoiceNumber())
    invoiceDatePicker.setValue(LocalDate.now())
    clientCombo.getSelectionModel.clearSelection()
    vatRateField.setText((currentDefaultVatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString())
    invoiceLinesData.clear()
    clearLineInputs()
    recalcTotals()

  private def loadInvoiceDetails(invoiceId: Int): Unit =
    invoiceService.loadDetails(invoiceId) match
      case Some(details) =>
        val totals = invoiceService.computeTotals(details.lines, details.invoice.vatRate)
        updateTotauxLabels(totals, details.invoice.vatRate)
        invoiceDetailsArea.setText(formatInvoiceDetails(details, totals))
      case None =>
        invoiceDetailsArea.clear()

  private def formatInvoiceDetails(details: InvoiceDetails, totals: InvoiceTotals): String =
    val builder = ListBuffer[String]()
    builder += s"Facture n° ${details.invoice.number}"
    builder += s"Date : ${details.invoice.date}"
    builder += s"Client : ${details.client.name}"
    builder += "Lignes :"
    details.lines.zipWithIndex.foreach { case (line, idx) =>
      builder += f"  ${idx + 1}%02d - ${line.description} | Qté: ${line.quantity} | PU: ${line.unitPriceHt} | Total: ${line.totalHt}"
    }
    builder += s"Sous-total : ${totals.subTotal}"
    builder += s"TVA : ${totals.vatAmount}"
    builder += s"Total TTC : ${totals.totalInclTax}"
    builder.mkString("\n")

  private def exportSelectedInvoice(): Unit =
    Option(invoicesTable.getSelectionModel.getSelectedItem) match
      case Some(row) =>
        invoiceService.loadDetails(row.invoiceId) match
          case Some(details) => onExportRequested(details)
          case None => DialogSupport.showError("Erreur", "Impossible de charger la facture sélectionnée.")
      case None =>
        DialogSupport.showError("Attention", "Sélectionnez une facture à exporter.")

  private def createSectionTitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-title")
    label

  private def createSubtitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-subtitle")
    label

  private def generateInvoiceNumber(): String =
    s"FAC-${LocalDate.now().getYear}-${UUID.randomUUID().toString.take(6).toUpperCase}"

  def reloadReferences(): Unit =
    clientsData.setAll(clientService.list()*)
    itemsData.setAll(itemService.list()*)

  def updateDefaultVat(rate: BigDecimal): Unit =
    currentDefaultVatRate = rate
    if vatRateField.getText.trim.isEmpty || invoiceLinesData.isEmpty then
      vatRateField.setText((currentDefaultVatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString())
    recalcTotals()
