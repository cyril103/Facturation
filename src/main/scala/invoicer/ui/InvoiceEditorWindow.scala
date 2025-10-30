package invoicer
package ui

import invoicer.model.{Client, InvoiceDetails, InvoiceLine, InvoiceTotals, Item}
import invoicer.service.{ClientService, InvoiceService, ItemService}
import invoicer.util.{DialogSupport, Formatting}

import java.time.LocalDate
import javafx.collections.FXCollections
import javafx.geometry.{Insets, Pos}
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.input.KeyEvent
import javafx.scene.layout.{BorderPane, GridPane, HBox, Priority, VBox}
import javafx.stage.{Modality, Stage, Window}
import javafx.util.StringConverter

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/** Fenetre modale pour la creation et l'edition d'une facture. */
private[ui] final class InvoiceEditorWindow(
    owner: Option[Window],
    clientService: ClientService,
    itemService: ItemService,
    invoiceService: InvoiceService,
    defaultVatRate: BigDecimal,
    onSaved: InvoiceDetails => Unit
):

  private case class InvoiceLineRow(itemId: Option[Int], description: String, quantity: BigDecimal, unitPrice: BigDecimal)

  private val clientsData = FXCollections.observableArrayList(clientService.list()*)
  private val itemsData = FXCollections.observableArrayList(itemService.list()*)
  private val invoiceLinesData = FXCollections.observableArrayList[InvoiceLineRow]()

  private val invoiceNumberField = new TextField()
  private val invoiceDatePicker = new DatePicker(LocalDate.now())
  private val clientCombo = new ComboBox[Client](clientsData)
  private val vatRateField = new TextField((defaultVatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString())

  private val itemCombo = new ComboBox[Item](itemsData)
  private val lineDescriptionField = new TextField()
  private val lineQuantityField = new TextField("1")
  private val lineUnitPriceField = new TextField("0.00")
  private val invoiceLinesTable = buildLinesTable()

  private val subTotalLabel = new Label("Sous-total HT : 0,00 ?")
  private val vatLabel = new Label(s"TVA (${formatVat(defaultVatRate)} %) : 0,00 ?")
  private val totalLabel = new Label("Total TTC : 0,00 ?")
  subTotalLabel.getStyleClass.add("totals-line")
  vatLabel.getStyleClass.add("totals-line")
  totalLabel.getStyleClass.add("totals-line-strong")

  private val editModeLabel = new Label("Creation d'une nouvelle facture")
  editModeLabel.getStyleClass.add("section-subtitle")

  private val saveButton = new Button("Enregistrer la facture")
  private val cancelButton = new Button("Fermer")

  private val stage = new Stage()
  owner.foreach(stage.initOwner)
  stage.initModality(Modality.WINDOW_MODAL)
  stage.setResizable(true)

  private var currentDefaultVatRate = defaultVatRate
  private var currentEditingInvoiceId: Option[Int] = None
  private var autoNumberEnabled = true

  initializeComboBoxes()
  initializeInvoiceNumberField()

  private val root = buildRoot()
  private val scene = new Scene(root, 820, 720)
  Option(getClass.getResource("/styles/app.css")).foreach(url => scene.getStylesheets.add(url.toExternalForm))
  stage.setScene(scene)

  configureButtons()
  recalcTotals()

  def showCreate(): Unit =
    reloadReferences()
    currentDefaultVatRate = defaultVatRate
    resetForm()
    stage.setTitle("Nouvelle facture")
    editModeLabel.setText("Creation d'une nouvelle facture")
    stage.showAndWait()

  def showEdit(details: InvoiceDetails): Unit =
    reloadReferences()
    populateForm(details)
    stage.setTitle(s"Edition ${details.invoice.number}")
    editModeLabel.setText(s"Edition de la facture ${details.invoice.number}")
    stage.showAndWait()

  private def buildRoot(): BorderPane =
    val editorContent = buildEditorContent()
    val scroll = new ScrollPane(editorContent)
    scroll.setFitToWidth(true)
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scroll.getStyleClass.add("editor-scroll")

    val root = new BorderPane(scroll)
    root.getStyleClass.addAll("card", "invoices-tab")
    root.setPadding(new Insets(24))

    val header = new VBox(8, createSectionTitle("Facture"))
    header.getStyleClass.add("section-header")
    root.setTop(header)

    root

  private def buildEditorContent(): VBox =
    val infoGrid = buildFormGrid()
    val infoBox = new VBox(12, createSubtitle("Informations de la facture"), infoGrid)
    infoBox.getStyleClass.add("form-card")

    val lineEditor = buildLineEditor()
    val addLineButton = new Button("Add")
    addLineButton.setOnAction(_ => addInvoiceLine())
    addLineButton.getStyleClass.add("primary-button")

    val removeLineButton = new Button("Retirer")
    removeLineButton.setOnAction(_ => removeSelectedLine())
    removeLineButton.getStyleClass.add("danger-button")

    val lineButtons = new HBox(10, addLineButton, removeLineButton)
    lineButtons.setAlignment(Pos.CENTER_LEFT)
    lineButtons.getStyleClass.add("button-row")

    val linesBox = new VBox(12, createSubtitle("Lignes de facture"), lineEditor, lineButtons, invoiceLinesTable)
    VBox.setVgrow(invoiceLinesTable, Priority.ALWAYS)
    linesBox.getStyleClass.add("form-card")

    val totalsBox = buildTotalsPane()

    saveButton.getStyleClass.add("primary-button")
    saveButton.setDefaultButton(true)
    saveButton.setOnAction(_ => saveInvoice())

    cancelButton.getStyleClass.add("ghost-button")
    cancelButton.setCancelButton(true)
    cancelButton.setOnAction(_ => stage.close())

    val buttons = new HBox(10, saveButton, cancelButton)
    buttons.setAlignment(Pos.CENTER_RIGHT)
    buttons.getStyleClass.add("button-row")

    val container = new VBox(20, editModeLabel, infoBox, linesBox, totalsBox, buttons)
    container.getStyleClass.add("editor-content")
    container

  private def buildFormGrid(): GridPane =
    val grid = new GridPane()
    grid.setHgap(12)
    grid.setVgap(12)
    grid.setPadding(new Insets(10))
    grid.getStyleClass.add("form-grid")

    grid.add(new Label("Numero *"), 0, 0)
    grid.add(invoiceNumberField, 1, 0)
    grid.add(new Label("Date *"), 0, 1)
    grid.add(invoiceDatePicker, 1, 1)
    grid.add(new Label("Client *"), 0, 2)
    grid.add(clientCombo, 1, 2)
    grid.add(new Label("TVA (%)"), 0, 3)
    grid.add(vatRateField, 1, 3)

    grid

  private def buildLineEditor(): GridPane =
    val grid = new GridPane()
    grid.setHgap(12)
    grid.setVgap(12)
    grid.getStyleClass.add("form-grid")

    grid.add(new Label("Article"), 0, 0)
    grid.add(itemCombo, 1, 0)
    grid.add(new Label("Description *"), 0, 1)
    grid.add(lineDescriptionField, 1, 1)
    grid.add(new Label("Quantite *"), 0, 2)
    grid.add(lineQuantityField, 1, 2)
    grid.add(new Label("PU HT *"), 0, 3)
    grid.add(lineUnitPriceField, 1, 3)

    grid

  private def buildLinesTable(): TableView[InvoiceLineRow] =
    val table = new TableView[InvoiceLineRow]()
    table.setPrefHeight(280)
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS)
    table.setItems(invoiceLinesData)
    table.getStyleClass.add("elevated-table")

    val descriptionCol = new TableColumn[InvoiceLineRow, String]("Description")
    descriptionCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(cell.getValue.description)
    )

    val quantityCol = new TableColumn[InvoiceLineRow, String]("Qte")
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

  private def buildTotalsPane(): VBox =
    val box = new VBox(6, subTotalLabel, vatLabel, totalLabel)
    box.setAlignment(Pos.BASELINE_RIGHT)
    box.getStyleClass.add("totals-panel")
    box

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

  private def configureButtons(): Unit =
    stage.setOnShown(_ => invoiceNumberField.requestFocus())

  private def reloadReferences(): Unit =
    clientsData.setAll(clientService.list()*)
    itemsData.setAll(itemService.list()*)

  private def initializeInvoiceNumberField(): Unit =
    invoiceNumberField.addEventFilter(KeyEvent.KEY_TYPED, _ => autoNumberEnabled = false)
    invoiceDatePicker.valueProperty().addListener { (_, _, newDate) =>
      if currentEditingInvoiceId.isEmpty && autoNumberEnabled && newDate != null then
        invoiceNumberField.setText(generateInvoiceNumber(newDate))
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
      if quantity <= 0 then throw IllegalArgumentException("La quantite doit etre positive.")

      val price = BigDecimal(lineUnitPriceField.getText.replace(",", ".").trim)
      if price < 0 then throw IllegalArgumentException("Le prix unitaire doit etre positif.")

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
    val vatRate = parseVatRateInput().toOption.getOrElse(currentDefaultVatRate)
    val lines = invoiceLinesData.asScala.toSeq.map(row =>
      InvoiceLine(
        id = None,
        invoiceId = currentEditingInvoiceId.getOrElse(0),
        itemId = row.itemId,
        description = row.description,
        quantity = row.quantity,
        unitPriceHt = row.unitPrice
      )
    )
    val totals = invoiceService.computeTotals(lines, vatRate)
    updateTotauxLabels(totals, vatRate)

  private def updateTotauxLabels(totals: InvoiceTotals, vatRate: BigDecimal): Unit =
    val vatPercent = (vatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros().toPlainString
    subTotalLabel.setText(s"Sous-total HT : ${Formatting.formatAmount(totals.subTotal)}")
    vatLabel.setText(s"TVA (${vatPercent} %) : ${Formatting.formatAmount(totals.vatAmount)}")
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
      DialogSupport.showError("Erreur", "Veuillez selectionner un client.")
      return

    val number = invoiceNumberField.getText.trim
    if number.isEmpty then
      DialogSupport.showError("Erreur", "Le numero de facture est obligatoire.")
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
              if wasEditing then s"Facture ${details.invoice.number} mise a jour."
              else s"Facture ${details.invoice.number} enregistree."
            DialogSupport.showInfo("Succes", message)
            onSaved(details)
            stage.close()
          case Failure(exception) =>
            DialogSupport.showError("Erreur", exception.getMessage)

  private def resetForm(): Unit =
    currentEditingInvoiceId = None
    editModeLabel.setText("Creation d'une nouvelle facture")
    val today = LocalDate.now()
    invoiceDatePicker.setValue(today)
    autoNumberEnabled = true
    invoiceNumberField.setText(generateInvoiceNumber(today))
    clientCombo.getSelectionModel.clearSelection()
    vatRateField.setText((currentDefaultVatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString())
    invoiceLinesData.clear()
    clearLineInputs()
    recalcTotals()

  private def populateForm(details: InvoiceDetails): Unit =
    currentEditingInvoiceId = details.invoice.id
    editModeLabel.setText(s"Edition de la facture ${details.invoice.number}")

    autoNumberEnabled = false
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

  private def createSectionTitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-title")
    label

  private def createSubtitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-subtitle")
    label

  private def generateInvoiceNumber(forDate: LocalDate): String =
    Try(invoiceService.nextInvoiceNumber(forDate)) match
      case Success(number) => number
      case Failure(_) =>
        val year = forDate.getYear
        s"FAC-$year-${java.util.UUID.randomUUID().toString.take(8).toUpperCase}"

  private def formatVat(rate: BigDecimal): String =
    (rate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros().toPlainString
