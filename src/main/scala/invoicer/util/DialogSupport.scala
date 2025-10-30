package invoicer
package util

import javafx.scene.control.{Alert, ButtonType}
import javafx.scene.control.Alert.AlertType

/** Helpers pour les boîtes de dialogue JavaFX. */
object DialogSupport:

  def showInfo(title: String, content: String): Unit =
    val alert = new Alert(AlertType.INFORMATION)
    alert.setTitle(title)
    alert.setHeaderText(null)
    alert.setContentText(content)
    alert.showAndWait()

  def showError(title: String, content: String): Unit =
    val alert = new Alert(AlertType.ERROR)
    alert.setTitle(title)
    alert.setHeaderText(null)
    alert.setContentText(content)
    alert.showAndWait()

  def confirm(title: String, content: String): Boolean =
    val alert = new Alert(AlertType.CONFIRMATION)
    alert.setTitle(title)
    alert.setHeaderText(null)
    alert.setContentText(content)
    val result = alert.showAndWait()
    result.isPresent && result.get() == ButtonType.OK
