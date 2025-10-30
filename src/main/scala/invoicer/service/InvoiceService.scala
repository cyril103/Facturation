package invoicer
package service

import invoicer.dao.InvoiceDAO
import invoicer.model.{Invoice, InvoiceDetails, InvoiceLine, InvoiceTotals}
import java.time.LocalDate

/** Logique métier liée aux factures. */
final class InvoiceService:

  def listWithClients(): Seq[(Invoice, String)] =
    InvoiceDAO.listWithClients().map { case (invoice, client) =>
      invoice -> client.name
    }

  def nextInvoiceNumber(forDate: LocalDate = LocalDate.now()): String =
    val year = forDate.getYear
    val sequence = InvoiceDAO.nextSequenceNumber(year)
    f"FAC-$year-$sequence%04d"

  def loadDetails(invoiceId: Int): Option[InvoiceDetails] =
    InvoiceDAO.findDetails(invoiceId)

  def create(
      number: String,
      date: LocalDate,
      clientId: Int,
      vatRate: BigDecimal,
      lines: Seq[InvoiceLine]
  ): InvoiceDetails =
    require(number.trim.nonEmpty, "Le numéro de facture est obligatoire")
    require(lines.nonEmpty, "La facture doit contenir au moins une ligne")
    require(vatRate >= 0, "La TVA doit être positive")

    val invoice = Invoice(None, number.trim, date, clientId, vatRate)
    val persisted = InvoiceDAO.insert(invoice, lines)
    InvoiceDAO.findDetails(persisted.id.get).getOrElse(
      throw new IllegalStateException("Impossible de relire la facture nouvellement créée")
    )

  def update(
      invoiceId: Int,
      number: String,
      date: LocalDate,
      clientId: Int,
      vatRate: BigDecimal,
      lines: Seq[InvoiceLine]
  ): InvoiceDetails =
    require(number.trim.nonEmpty, "Le numéro de facture est obligatoire")
    require(lines.nonEmpty, "La facture doit contenir au moins une ligne")
    require(vatRate >= 0, "La TVA doit être positive")

    val invoice = Invoice(Some(invoiceId), number.trim, date, clientId, vatRate)
    InvoiceDAO.update(invoice, lines)
    InvoiceDAO.findDetails(invoiceId).getOrElse(
      throw new IllegalStateException("Impossible de relire la facture mise à jour")
    )

  def computeTotals(lines: Seq[InvoiceLine], vatRate: BigDecimal): InvoiceTotals =
    val subTotal = lines.map(_.totalHt).foldLeft(BigDecimal(0))(_ + _)
    val vatAmount = (subTotal * vatRate).setScale(2, BigDecimal.RoundingMode.HALF_UP)
    val total = (subTotal + vatAmount).setScale(2, BigDecimal.RoundingMode.HALF_UP)
    InvoiceTotals(
      subTotal.setScale(2, BigDecimal.RoundingMode.HALF_UP),
      vatAmount,
      total
    )
