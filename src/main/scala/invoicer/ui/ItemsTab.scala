package invoicer
package ui

import invoicer.model.Item
import invoicer.service.ItemService
import invoicer.util.{DialogSupport, Formatting}

import javafx.collections.FXCollections
import javafx.geometry.{Insets, Pos}
import javafx.scene.control._
import javafx.scene.layout.{BorderPane, GridPane, HBox, VBox}

import scala.util.{Failure, Success, Try}

/** Onglet de gestion des articles/prestations. */
final class ItemsTab(itemService: ItemService) extends Tab:
  setText("Articles")
  closableProperty().set(false)

  private val table = new TableView[Item]()
  table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS)
  table.getStyleClass.add("elevated-table")
  private val itemsData = FXCollections.observableArrayList[Item]()

  private val searchField = new TextField()
  searchField.setPromptText("Rechercher par nom...")
  searchField.getStyleClass.add("search-field")

  private val nameField = new TextField()
  private val descriptionField = new TextArea()
  descriptionField.setPrefRowCount(2)
  private val priceField = new TextField()

  initializeTable()
  setContent(buildContent())
  refreshTable()

  private def initializeTable(): Unit =
    val nameCol = new TableColumn[Item, String]("Nom")
    nameCol.setCellValueFactory(cell => javafx.beans.property.SimpleStringProperty(cell.getValue.name))

    val priceCol = new TableColumn[Item, String]("Prix HT")
    priceCol.setCellValueFactory(cell =>
      javafx.beans.property.SimpleStringProperty(Formatting.formatAmount(cell.getValue.unitPriceHt))
    )

    table.getColumns.addAll(nameCol, priceCol)
    table.setItems(itemsData)
    table.getSelectionModel.selectedItemProperty().addListener { (_, _, item) =>
      if item != null then populateForm(item)
    }

  private def buildContent(): BorderPane =
    val root = new BorderPane()
    root.getStyleClass.addAll("card", "items-tab")
    root.setPadding(new Insets(24))

    val header = new VBox(12, createSectionTitle("Catalogue d'articles"), buildSearchPane())
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

    grid.add(new Label("Nom *"), 0, 0)
    grid.add(nameField, 1, 0)
    grid.add(new Label("Description"), 0, 1)
    grid.add(descriptionField, 1, 1)
    grid.add(new Label("Prix HT *"), 0, 2)
    grid.add(priceField, 1, 2)

    val saveButton = new Button("Enregistrer")
    saveButton.setDefaultButton(true)
    saveButton.setOnAction(_ => saveItem())
    saveButton.getStyleClass.add("primary-button")

    val newButton = new Button("Nouveau")
    newButton.setOnAction { _ =>
      table.getSelectionModel.clearSelection()
      clearForm()
    }
    newButton.getStyleClass.add("ghost-button")

    val deleteButton = new Button("Supprimer")
    deleteButton.setOnAction(_ => deleteItem())
    deleteButton.getStyleClass.add("danger-button")

    val buttons = new HBox(10, saveButton, newButton, deleteButton)
    buttons.setAlignment(Pos.CENTER_RIGHT)
    buttons.getStyleClass.add("button-row")

    val sectionTitle = createSubtitle("Informations de l'article")
    val formBox = new VBox(16, sectionTitle, grid, buttons)
    formBox.getStyleClass.add("form-card")
    formBox

  private def refreshTable(): Unit =
    itemsData.setAll(itemService.list()*)

  private def performSearch(): Unit =
    val query = searchField.getText
    val results = itemService.search(query)
    itemsData.setAll(results*)

  private def populateForm(item: Item): Unit =
    nameField.setText(item.name)
    descriptionField.setText(item.description.getOrElse(""))
    priceField.setText(item.unitPriceHt.bigDecimal.stripTrailingZeros().toPlainString)

  private def clearForm(): Unit =
    nameField.clear()
    descriptionField.clear()
    priceField.clear()

  private def buildItemFromForm(existing: Option[Item]): Item =
    val price = Try(BigDecimal(priceField.getText.trim)).getOrElse(BigDecimal(-1))
    val base = existing.getOrElse(Item(None, "", BigDecimal(0), None))
    base.copy(
      name = nameField.getText.trim,
      description = Option(descriptionField.getText).map(_.trim).filter(_.nonEmpty),
      unitPriceHt = price
    )

  private def saveItem(): Unit =
    val existing = Option(table.getSelectionModel.getSelectedItem)
    Try {
      val item = buildItemFromForm(existing)
      itemService.save(item)
    } match
      case Success(saved) =>
        DialogSupport.showInfo("Succès", s"Article ${saved.name} enregistré.")
        refreshTable()
        table.getSelectionModel.clearSelection()
        clearForm()
      case Failure(exception) =>
        DialogSupport.showError("Erreur", exception.getMessage)

  private def deleteItem(): Unit =
    Option(table.getSelectionModel.getSelectedItem) match
      case Some(item) =>
        val confirmed = DialogSupport.confirm("Confirmation", s"Supprimer l'article ${item.name} ?")
        if confirmed then
          Try(itemService.delete(item)) match
            case Success(_) =>
              refreshTable()
              clearForm()
            case Failure(exception) =>
              DialogSupport.showError("Erreur", exception.getMessage)
      case None =>
        DialogSupport.showError("Attention", "Sélectionnez un article à supprimer.")

  private def createSectionTitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-title")
    label

  private def createSubtitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-subtitle")
    label
