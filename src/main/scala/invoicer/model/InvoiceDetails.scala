package invoicer
package model

/** Projection regroupant facture, client et lignes. */
final case class InvoiceDetails(
    invoice: Invoice,
    client: Client,
    lines: Seq[InvoiceLine]
)
