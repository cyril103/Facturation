package invoicer
package model

/** Ligne d'une facture. */
final case class InvoiceLine(
    id: Option[Int],
    invoiceId: Int,
    itemId: Option[Int],
    description: String,
    quantity: BigDecimal,
    unitPriceHt: BigDecimal
) :

  /** Total hors taxe pour la ligne. */
  def totalHt: BigDecimal =
    (quantity * unitPriceHt).setScale(2, BigDecimal.RoundingMode.HALF_UP)
