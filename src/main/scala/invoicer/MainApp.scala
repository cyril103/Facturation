package invoicer

import invoicer.service._
import invoicer.ui._
import invoicer.util.{DesktopSupport, DialogSupport}
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.{ScrollPane, TabPane}
import javafx.stage.Stage

/** Point d'entrée JavaFX. */
final class MainApp extends Application:

  override def start(primaryStage: Stage): Unit =
    val clientService = new ClientService()
    val itemService = new ItemService()
    val companyService = new CompanyService()
    val settingsService = new SettingsService()
    val invoiceService = new InvoiceService()
    val pdfService = new PdfService(invoiceService, companyService)

    val clientsTab = new ClientsTab(clientService)
    val itemsTab = new ItemsTab(itemService)

    val invoicesTab = new InvoicesTab(
      clientService = clientService,
      itemService = itemService,
      invoiceService = invoiceService,
      onExportRequested = details =>
        try
          val path = pdfService.exportInvoice(details)
          DialogSupport.showInfo("PDF généré", s"Facture exportée : ${path.toAbsolutePath}")
        catch
          case ex: Exception =>
            DialogSupport.showError("Erreur", s"Impossible de générer le PDF : ${ex.getMessage}")
      ,
      onPreviewRequested = details =>
        try
          val path = pdfService.exportInvoice(details)
          DesktopSupport.open(path)
        catch
          case ex: Exception =>
            DialogSupport.showError("Erreur", s"Impossible d'afficher l'aperçu : ${ex.getMessage}")
      ,
      defaultVatRate = settingsService.vatRate()
    )

    val settingsTab = new SettingsTab(
      companyService = companyService,
      settingsService = settingsService,
      onVatChanged = rate => invoicesTab.updateDefaultVat(rate)
    )

    val tabPane = new TabPane(clientsTab, itemsTab, invoicesTab, settingsTab)
    tabPane.getStyleClass.add("main-tab-pane")

    val scrollableRoot = new ScrollPane(tabPane)
    scrollableRoot.setFitToWidth(true)
    scrollableRoot.setFitToHeight(true)
    scrollableRoot.setPannable(true)
    scrollableRoot.getStyleClass.add("app-root")

    tabPane.getSelectionModel.selectedItemProperty().addListener { (_, _, tab) =>
      if tab eq invoicesTab then
        invoicesTab.reloadReferences()
    }

    val scene = new Scene(scrollableRoot, 1024, 720)
    val stylesheetUrl = getClass.getResource("/styles/app.css")
    if stylesheetUrl != null then scene.getStylesheets.add(stylesheetUrl.toExternalForm)

    primaryStage.setTitle("Invoicer - Gestion de facturation")
    primaryStage.setMinWidth(900)
    primaryStage.setMinHeight(620)
    primaryStage.setScene(scene)
    primaryStage.show()

object MainApp:
  def main(args: Array[String]): Unit =
    Application.launch(classOf[MainApp], args*)
