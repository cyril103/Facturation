package invoicer
package ui

import invoicer.model.Client
import invoicer.service.ClientService
import invoicer.util.DialogSupport

import javafx.collections.FXCollections
import javafx.geometry.{Insets, Pos}
import javafx.scene.control._
import javafx.scene.layout.{BorderPane, GridPane, HBox, VBox}

import scala.util.{Failure, Success, Try}

/** Onglet de gestion des clients. */
final class ClientsTab(clientService: ClientService) extends Tab:
  setText("Clients")
  closableProperty().set(false)

  private val table = new TableView[Client]()
  table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS)
  table.getStyleClass.add("elevated-table")
  private val clientsData = FXCollections.observableArrayList[Client]()

  private val searchField = new TextField()
  searchField.setPromptText("Rechercher par nom...")
  searchField.getStyleClass.add("search-field")

  private val nameField = new TextField()
  private val addressField = new TextArea()
  addressField.setPrefRowCount(2)
  private val emailField = new TextField()
  private val phoneField = new TextField()
  private val siretField = new TextField()

  initializeTable()
  setContent(buildContent())
  refreshTable()

  private def initializeTable(): Unit =
    val nameCol = new TableColumn[Client, String]("Nom")
    nameCol.setCellValueFactory(cell => javafx.beans.property.SimpleStringProperty(cell.getValue.name))

    val emailCol = new TableColumn[Client, String]("Email")
    emailCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(cell.getValue.email.getOrElse(""))
    )

    val phoneCol = new TableColumn[Client, String]("Téléphone")
    phoneCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(cell.getValue.phone.getOrElse(""))
    )

    table.getColumns.addAll(nameCol, emailCol, phoneCol)
    table.setItems(clientsData)
    table.getSelectionModel.selectedItemProperty().addListener { (_, _, client) =>
      if client != null then populateForm(client)
    }

  private def buildContent(): BorderPane =
    val root = new BorderPane()
    root.getStyleClass.addAll("card", "clients-tab")
    root.setPadding(new Insets(24))

    val header = new VBox(12, createSectionTitle("Gestion des clients"), buildSearchPane())
    header.getStyleClass.add("section-header")
    root.setTop(header)
    root.setCenter(table)
    root.setBottom(buildFormPane())

    root

  private def buildSearchPane(): HBox =
    val searchButton = new Button("Chercher")
    searchButton.setOnAction(_ => performSearch())
    searchButton.getStyleClass.add("primary-button")

    val resetButton = new Button("Reinitialiser")
    resetButton.setOnAction { _ =>
      searchField.clear()
      refreshTable()
    }
    resetButton.getStyleClass.add("ghost-button")

    val box = new HBox(12, searchField, searchButton, resetButton)
    box.setAlignment(Pos.CENTER_LEFT)
    box.getStyleClass.add("toolbar")
    box

  private def buildFormPane(): VBox =
    val grid = new GridPane()
    grid.setHgap(10)
    grid.setVgap(10)
    grid.setPadding(new Insets(10))
    grid.getStyleClass.add("form-grid")

    val labelsAndNodes = Seq(
      "Nom *" -> nameField,
      "Adresse" -> addressField,
      "Email" -> emailField,
      "Téléphone" -> phoneField,
      "SIRET" -> siretField
    )

    labelsAndNodes.zipWithIndex.foreach { case ((label, node), idx) =>
      grid.add(new Label(label), 0, idx)
      grid.add(node, 1, idx)
    }

    val saveButton = new Button("Enregistrer")
    saveButton.setDefaultButton(true)
    saveButton.setOnAction(_ => saveClient())
    saveButton.getStyleClass.add("primary-button")

    val newButton = new Button("Nouveau")
    newButton.setOnAction { _ =>
      table.getSelectionModel.clearSelection()
      clearForm()
    }
    newButton.getStyleClass.add("ghost-button")

    val deleteButton = new Button("Supprimer")
    deleteButton.setOnAction(_ => deleteClient())
    deleteButton.getStyleClass.add("danger-button")

    val buttons = new HBox(10, saveButton, newButton, deleteButton)
    buttons.setAlignment(Pos.CENTER_RIGHT)
    buttons.getStyleClass.add("button-row")

    val sectionTitle = createSubtitle("Informations client")
    val formBox = new VBox(16, sectionTitle, grid, buttons)
    formBox.getStyleClass.add("form-card")
    formBox

  private def refreshTable(): Unit =
    clientsData.setAll(clientService.list()*)

  private def performSearch(): Unit =
    val query = searchField.getText
    val results = clientService.search(query)
    clientsData.setAll(results*)

  private def populateForm(client: Client): Unit =
    nameField.setText(client.name)
    addressField.setText(client.address.getOrElse(""))
    emailField.setText(client.email.getOrElse(""))
    phoneField.setText(client.phone.getOrElse(""))
    siretField.setText(client.siret.getOrElse(""))

  private def clearForm(): Unit =
    nameField.clear()
    addressField.clear()
    emailField.clear()
    phoneField.clear()
    siretField.clear()

  private def buildClientFromForm(existing: Option[Client]): Client =
    existing match
      case Some(client) =>
        client.copy(
          name = nameField.getText.trim,
          address = Option(addressField.getText).map(_.trim).filter(_.nonEmpty),
          email = Option(emailField.getText).map(_.trim).filter(_.nonEmpty),
          phone = Option(phoneField.getText).map(_.trim).filter(_.nonEmpty),
          siret = Option(siretField.getText).map(_.trim).filter(_.nonEmpty)
        )
      case None =>
        Client(
          id = None,
          name = nameField.getText.trim,
          address = Option(addressField.getText).map(_.trim).filter(_.nonEmpty),
          email = Option(emailField.getText).map(_.trim).filter(_.nonEmpty),
          phone = Option(phoneField.getText).map(_.trim).filter(_.nonEmpty),
          siret = Option(siretField.getText).map(_.trim).filter(_.nonEmpty)
        )

  private def saveClient(): Unit =
    val existing = Option(table.getSelectionModel.getSelectedItem)
    Try(clientService.save(buildClientFromForm(existing))) match
      case Success(saved) =>
        DialogSupport.showInfo("Succès", s"Client ${saved.name} enregistré.")
        refreshTable()
        table.getSelectionModel.clearSelection()
        clearForm()
      case Failure(exception) =>
        DialogSupport.showError("Erreur", exception.getMessage)

  private def deleteClient(): Unit =
    Option(table.getSelectionModel.getSelectedItem) match
      case Some(client) =>
        val confirmed = DialogSupport.confirm("Confirmation", s"Supprimer le client ${client.name} ?")
        if confirmed then
          Try(clientService.delete(client)) match
            case Success(_) =>
              refreshTable()
              clearForm()
            case Failure(exception) =>
              DialogSupport.showError("Erreur", exception.getMessage)
      case None =>
        DialogSupport.showError("Attention", "Sélectionnez un client à supprimer.")

  private def createSectionTitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-title")
    label

  private def createSubtitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-subtitle")
    label
