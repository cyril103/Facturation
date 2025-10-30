package invoicer
package ui

import invoicer.model.Company
import invoicer.service.{CompanyService, SettingsService}
import invoicer.util.DialogSupport

import javafx.geometry.{Insets, Pos}
import javafx.scene.control._
import javafx.scene.layout.{GridPane, HBox, VBox}

import scala.util.{Failure, Success, Try}

/** Onglet de paramétrage de la société et de la TVA. */
final class SettingsTab(
    companyService: CompanyService,
    settingsService: SettingsService,
    onVatChanged: BigDecimal => Unit
) extends Tab:
  setText("Paramètres")
  closableProperty().set(false)

  private var currentCompany: Company = companyService.load()

  private val vatField = new TextField()
  private val nameField = new TextField()
  private val addressField = new TextArea()
  addressField.setPrefRowCount(3)
  private val emailField = new TextField()
  private val phoneField = new TextField()
  private val siretField = new TextField()

  loadData()
  setContent(buildContent())

  private def buildContent(): VBox =
    val vatBox = buildVatSection()
    val companyBox = buildCompanySection()
    val root = new VBox(20, createSectionTitle("Parametres generaux"), vatBox, companyBox)
    root.getStyleClass.addAll("card", "settings-tab")
    root.setPadding(new Insets(24))
    root

  private def buildVatSection(): VBox =
    val grid = new GridPane()
    grid.setHgap(10)
    grid.setVgap(10)
    grid.setPadding(new Insets(10))
    grid.getStyleClass.add("form-grid")

    grid.add(new Label("TVA (%)"), 0, 0)
    grid.add(vatField, 1, 0)

    val saveButton = new Button("Enregistrer la TVA")
    saveButton.setOnAction(_ => saveVat())
    val buttons = new HBox(10, saveButton)
    buttons.setAlignment(Pos.CENTER_RIGHT)
    buttons.getStyleClass.add("button-row")
    saveButton.getStyleClass.add("primary-button")
    grid.add(buttons, 1, 1)

    val box = new VBox(12, createSubtitle("TVA par defaut"), grid)
    box.getStyleClass.add("form-card")
    box

  private def buildCompanySection(): VBox =
    val grid = new GridPane()
    grid.setHgap(10)
    grid.setVgap(10)
    grid.setPadding(new Insets(10))
    grid.getStyleClass.add("form-grid")

    grid.add(new Label("Nom *"), 0, 0)
    grid.add(nameField, 1, 0)

    grid.add(new Label("Adresse"), 0, 1)
    grid.add(addressField, 1, 1)

    grid.add(new Label("Email"), 0, 2)
    grid.add(emailField, 1, 2)

    grid.add(new Label("Téléphone"), 0, 3)
    grid.add(phoneField, 1, 3)

    grid.add(new Label("SIRET"), 0, 4)
    grid.add(siretField, 1, 4)

    val saveButton = new Button("Enregistrer")
    saveButton.setOnAction(_ => saveCompany())
    val buttons = new HBox(10, saveButton)
    buttons.setAlignment(Pos.CENTER_RIGHT)
    buttons.getStyleClass.add("button-row")
    saveButton.getStyleClass.add("primary-button")
    grid.add(buttons, 1, 5)

    val box = new VBox(12, createSubtitle("Coordonnees de l'entreprise"), grid)
    box.getStyleClass.add("form-card")
    box

  private def loadData(): Unit =
    val vatRate = settingsService.vatRate()
    vatField.setText((vatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toString())

    nameField.setText(currentCompany.name)
    addressField.setText(currentCompany.address.getOrElse(""))
    emailField.setText(currentCompany.email.getOrElse(""))
    phoneField.setText(currentCompany.phone.getOrElse(""))
    siretField.setText(currentCompany.siret.getOrElse(""))

  private def saveVat(): Unit =
    val parsed = Try(BigDecimal(vatField.getText.replace(",", ".").trim))
    parsed match
      case Success(value) if value >= 0 =>
        val normalized = (value / BigDecimal(100)).setScale(4, BigDecimal.RoundingMode.HALF_UP)
        settingsService.updateVatRate(normalized)
        onVatChanged(normalized)
        DialogSupport.showInfo("Succès", "Taux de TVA mis à jour.")
      case Success(_) =>
        DialogSupport.showError("Erreur", "Le taux de TVA doit être positif.")
      case Failure(_) =>
        DialogSupport.showError("Erreur", "Valeur de TVA invalide.")

  private def saveCompany(): Unit =
    val company = Company(
      id = currentCompany.id,
      name = nameField.getText.trim,
      address = Option(addressField.getText).map(_.trim).filter(_.nonEmpty),
      email = Option(emailField.getText).map(_.trim).filter(_.nonEmpty),
      phone = Option(phoneField.getText).map(_.trim).filter(_.nonEmpty),
      siret = Option(siretField.getText).map(_.trim).filter(_.nonEmpty)
    )
    Try(companyService.save(company)) match
      case Success(saved) =>
        currentCompany = saved
        DialogSupport.showInfo("Succès", "Coordonnées enregistrées.")
      case Failure(exception) =>
        DialogSupport.showError("Erreur", exception.getMessage)

  private def createSectionTitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-title")
    label

  private def createSubtitle(text: String): Label =
    val label = new Label(text)
    label.getStyleClass.add("section-subtitle")
    label
