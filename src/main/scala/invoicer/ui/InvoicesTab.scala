package invoicer
package ui

import invoicer.model.{InvoiceDetails, InvoiceTotals}
import invoicer.service.{ClientService, InvoiceService, ItemService}
import invoicer.util.{DialogSupport, Formatting}

import java.time.LocalDate
import javafx.collections.FXCollections
import javafx.geometry.{Insets, Pos}
import javafx.scene.control._
import javafx.scene.layout.{BorderPane, HBox, Priority, VBox}
import javafx.stage.Window

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

/** Onglet d'edition et d'export des factures. */
final class InvoicesTab(
    clientService: ClientService,
    itemService: ItemService,
    invoiceService: InvoiceService,
    onExportRequested: InvoiceDetails => Unit,
    onPreviewRequested: InvoiceDetails => Unit,
    defaultVatRate: BigDecimal
) extends Tab:
  setText("Factures")
  closableProperty().set(false)

  private case class InvoiceListRow(invoiceId: Int, number: String, date: LocalDate, clientName: String)

  private val invoicesData = FXCollections.observableArrayList[InvoiceListRow]()
  private val invoicesTable = buildInvoicesTable()
  private val invoiceDetailsArea = new TextArea()
  invoiceDetailsArea.setEditable(false)
  invoiceDetailsArea.setPromptText("Selectionnez une facture pour afficher ses details...")
  invoiceDetailsArea.getStyleClass.add("details-area")

  private var currentDefaultVatRate = defaultVatRate

  refreshInvoices()
  setContent(buildContent())

  private def buildContent(): BorderPane =
    val root = new BorderPane()
    root.getStyleClass.addAll("card", "invoices-tab")
    root.setPadding(new Insets(24))

    val header = new VBox(8, createSectionTitle("Gestion des factures"))
    header.getStyleClass.add("section-header")
    root.setTop(header)

    root.setCenter(buildMainSection())
    root

  private def buildMainSection(): VBox =
    val createButton = new Button("Creer facture")
    createButton.setOnAction(_ => openCreateWindow())
    createButton.getStyleClass.add("primary-button")

    val editButton = new Button("Editer facture")
    editButton.setOnAction(_ => openEditorForSelectedInvoice())
    editButton.getStyleClass.add("ghost-button")

    val previewButton = new Button("Apercu")
    previewButton.setOnAction(_ => previewSelectedInvoice())
    previewButton.getStyleClass.add("ghost-button")

    val exportButton = new Button("Exporter en PDF")
    exportButton.setOnAction(_ => exportSelectedInvoice())
    exportButton.getStyleClass.add("ghost-button")

    val refreshButton = new Button("Rafraichir")
    refreshButton.setOnAction(_ => refreshInvoices())
    refreshButton.getStyleClass.add("ghost-button")

    val buttons = new HBox(10, createButton, editButton, previewButton, exportButton, refreshButton)
    buttons.setAlignment(Pos.CENTER_RIGHT)
    buttons.getStyleClass.add("button-row")

    val tableTitle = createSubtitle("Factures existantes")
    val detailsLabel = createSubtitle("Details de la facture")

    val wrapper = new VBox(14, tableTitle, invoicesTable, buttons, detailsLabel, invoiceDetailsArea)
    VBox.setVgrow(invoicesTable, Priority.ALWAYS)
    VBox.setVgrow(invoiceDetailsArea, Priority.ALWAYS)
    wrapper.getStyleClass.add("list-card")
    wrapper

  private def buildInvoicesTable(): TableView[InvoiceListRow] =
    val table = new TableView[InvoiceListRow]()
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS)
    table.getStyleClass.add("elevated-table")

    val numberCol = new TableColumn[InvoiceListRow, String]("Numero")
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

  private def refreshInvoices(): Unit =
    val rows = invoiceService.listWithClients().collect { case (invoice, clientName) =>
      InvoiceListRow(invoice.id.getOrElse(0), invoice.number, invoice.date, clientName)
    }
    invoicesData.setAll(rows*)

  private def openCreateWindow(): Unit =
    val window = new InvoiceEditorWindow(
      owner = ownerWindow,
      clientService = clientService,
      itemService = itemService,
      invoiceService = invoiceService,
      defaultVatRate = currentDefaultVatRate,
      onSaved = handleInvoiceSaved
    )
    window.showCreate()

  private def openEditorForSelectedInvoice(): Unit =
    Option(invoicesTable.getSelectionModel.getSelectedItem) match
      case Some(row) =>
        invoiceService.loadDetails(row.invoiceId) match
          case Some(details) =>
            val window = new InvoiceEditorWindow(
              owner = ownerWindow,
              clientService = clientService,
              itemService = itemService,
              invoiceService = invoiceService,
              defaultVatRate = currentDefaultVatRate,
              onSaved = handleInvoiceSaved
            )
            window.showEdit(details)
          case None =>
            DialogSupport.showError("Erreur", "Impossible de charger la facture selectionnee pour edition.")
      case None =>
        DialogSupport.showInfo("Information", "Selectionnez une facture a editer ou utilisez 'Creer facture'.")

  private def handleInvoiceSaved(details: InvoiceDetails): Unit =
    refreshInvoices()
    details.invoice.id.foreach(selectInvoiceRow)
    details.invoice.id.foreach(loadInvoiceDetails)

  private def selectInvoiceRow(invoiceId: Int): Unit =
    invoicesData.asScala.find(_.invoiceId == invoiceId).foreach { row =>
      invoicesTable.getSelectionModel.select(row)
      invoicesTable.scrollTo(row)
    }

  private def ownerWindow: Option[Window] =
    Option(getTabPane).flatMap(tp => Option(tp.getScene)).map(_.getWindow)

  private def loadInvoiceDetails(invoiceId: Int): Unit =
    invoiceService.loadDetails(invoiceId) match
      case Some(details) =>
        val totals = invoiceService.computeTotals(details.lines, details.invoice.vatRate)
        invoiceDetailsArea.setText(formatInvoiceDetails(details, totals))
      case None =>
        invoiceDetailsArea.clear()

  private def formatInvoiceDetails(details: InvoiceDetails, totals: InvoiceTotals): String =
    val builder = ListBuffer[String]()
    builder += s"Facture no ${details.invoice.number}"
    builder += s"Date : ${details.invoice.date}"
    builder += s"Client : ${details.client.name}"
    builder += "Lignes :"
    details.lines.zipWithIndex.foreach { case (line, idx) =>
      builder += f"  ${idx + 1}%02d - ${line.description} | Qte: ${line.quantity} | PU: ${line.unitPriceHt} | Total: ${Formatting.formatAmount(line.totalHt)}"
    }
    builder += s"Sous-total : ${Formatting.formatAmount(totals.subTotal)}"
    builder += s"TVA : ${Formatting.formatAmount(totals.vatAmount)}"
    builder += s"Total TTC : ${Formatting.formatAmount(totals.totalInclTax)}"
    builder.mkString("\n")

  private def exportSelectedInvoice(): Unit =
    Option(invoicesTable.getSelectionModel.getSelectedItem) match
      case Some(row) =>
        invoiceService.loadDetails(row.invoiceId) match
          case Some(details) => onExportRequested(details)
          case None => DialogSupport.showError("Erreur", "Impossible de charger la facture selectionnee.")
      case None =>
        DialogSupport.showError("Attention", "Selectionnez une facture a exporter.")

  private def previewSelectedInvoice(): Unit =
    Option(invoicesTable.getSelectionModel.getSelectedItem) match
      case Some(row) =>
        invoiceService.loadDetails(row.invoiceId) match
          case Some(details) => onPreviewRequested(details)
          case None => DialogSupport.showError("Erreur", "Impossible de charger la facture selectionnee.")
      case None =>
        DialogSupport.showError("Attention", "Selectionnez une facture a previsualiser.")

  private def createSectionTitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-title")
    label

  private def createSubtitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-subtitle")
    label

  def reloadReferences(): Unit =
    refreshInvoices()

  def updateDefaultVat(rate: BigDecimal): Unit =
    currentDefaultVatRate = rate
