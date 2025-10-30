package invoicer
package util

import java.text.NumberFormat
import java.util.Locale

/** Fonctions utilitaires pour l'affichage. */
object Formatting:
  private val currencyFormat =
    val fmt = NumberFormat.getCurrencyInstance(Locale.FRANCE)
    fmt.setMinimumFractionDigits(2)
    fmt.setMaximumFractionDigits(2)
    fmt

  def formatAmount(amount: BigDecimal): String =
    currencyFormat.format(amount.bigDecimal)
