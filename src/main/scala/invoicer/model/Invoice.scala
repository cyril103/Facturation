package invoicer
package model

import java.time.LocalDate

/** Facture avec référence client et TVA. */
final case class Invoice(
    id: Option[Int],
    number: String,
    date: LocalDate,
    clientId: Int,
    vatRate: BigDecimal
)
