package invoicer
package util

import java.awt.Desktop
import java.nio.file.Path

/** Helpers utilitaires pour lancer les apercus PDF via le systeme. */
object DesktopSupport:

  def open(path: Path): Unit =
    if Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.OPEN) then
      Desktop.getDesktop.open(path.toFile)
    else
      throw UnsupportedOperationException(
        s"L'ouverture de fichier n'est pas supportee sur cette plateforme. PDF genere : ${path.toAbsolutePath}"
      )
