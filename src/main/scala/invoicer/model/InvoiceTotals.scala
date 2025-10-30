package invoicer
package model

/** Totaux calculés pour une facture. */
final case class InvoiceTotals(
    subTotal: BigDecimal,
    vatAmount: BigDecimal,
    totalInclTax: BigDecimal
)
