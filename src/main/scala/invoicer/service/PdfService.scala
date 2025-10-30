package invoicer
package service

import invoicer.model.InvoiceDetails
import invoicer.util.Formatting
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.PDPageContentStream

import java.nio.file.{Files, Path, Paths}
import java.util.logging.{Level, Logger}
import scala.util.Using

/** Génération de PDF à partir d'une facture. */
final class PdfService(
    invoiceService: InvoiceService,
    companyService: CompanyService
):

  private val outputDirectory: Path =
    val dir = Paths.get(System.getProperty("user.home"), "app", "factures")
    Files.createDirectories(dir)
    dir

  configurePdfBox()

  private def configurePdfBox(): Unit =
    val cacheDir = outputDirectory.resolve("pdfbox-font-cache")
    Files.createDirectories(cacheDir)
    // Configure PDFBox to store its font cache in the application folder to avoid repeated scan warnings.
    System.setProperty("org.apache.pdfbox.fontcache", cacheDir.toAbsolutePath.toString)
    // Silence verbose PDFBox logging that looked like errors in console output.
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error")
    Logger.getLogger("org.apache.pdfbox.pdmodel.font.FileSystemFontProvider").setLevel(Level.SEVERE)
    Logger.getLogger("org.apache.pdfbox.util.PDFStreamEngine").setLevel(Level.SEVERE)

  def exportInvoice(details: InvoiceDetails): Path =
    val totals = invoiceService.computeTotals(details.lines, details.invoice.vatRate)
    val fileName = s"FACTURE_${details.invoice.number.replaceAll("[^A-Za-z0-9_-]", "_")}.pdf"
    val outputPath = outputDirectory.resolve(fileName)

    Using.resource(new PDDocument()) { document =>
      val page = new PDPage(PDRectangle.A4)
      document.addPage(page)

      val company = companyService.load()

      Using.resource(new PDPageContentStream(document, page)) { contentStream =>
        val margin = 50f
        val pageWidth = page.getMediaBox.getWidth
        val startX = margin
        var cursorY = page.getMediaBox.getHeight - margin

        // En-tête entreprise
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14)
        contentStream.newLineAtOffset(startX, cursorY)
        contentStream.showText(company.name)
        contentStream.endText()

        cursorY -= 20
        val companyLines = Seq(
          company.address.getOrElse(""),
          company.email.map(e => s"Email : $e").getOrElse(""),
          company.phone.map(p => s"Téléphone : $p").getOrElse(""),
          company.siret.map(s => s"SIRET : $s").getOrElse("")
        ).filter(_.nonEmpty)

        companyLines.foreach { line =>
          cursorY -= 14
          contentStream.beginText()
          contentStream.setFont(PDType1Font.HELVETICA, 12)
          contentStream.newLineAtOffset(startX, cursorY)
          contentStream.showText(line)
          contentStream.endText()
        }

        // Titre facture
        cursorY -= 40
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18)
        val title = s"FACTURE ${details.invoice.number}"
        val titleWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(title) / 1000f * 18
        contentStream.newLineAtOffset(pageWidth - margin - titleWidth, cursorY)
        contentStream.showText(title)
        contentStream.endText()

        cursorY -= 20
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA, 12)
        val dateText = s"Date : ${details.invoice.date}"
        val dateWidth = PDType1Font.HELVETICA.getStringWidth(dateText) / 1000f * 12
        contentStream.newLineAtOffset(pageWidth - margin - dateWidth, cursorY)
        contentStream.showText(dateText)
        contentStream.endText()

        // Coordonnées client
        cursorY -= 40
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 13)
        contentStream.newLineAtOffset(startX, cursorY)
        contentStream.showText("Client")
        contentStream.endText()

        val clientLines = Seq(
          details.client.name,
          details.client.address.getOrElse(""),
          details.client.email.getOrElse(""),
          details.client.phone.getOrElse(""),
          details.client.siret.map(s => s"SIRET : $s").getOrElse("")
        ).filter(_.nonEmpty)

        clientLines.foreach { line =>
          cursorY -= 16
          contentStream.beginText()
          contentStream.setFont(PDType1Font.HELVETICA, 12)
          contentStream.newLineAtOffset(startX, cursorY)
          contentStream.showText(line)
          contentStream.endText()
        }

        // Tableau des lignes
        cursorY -= 30
        val tableStartY = cursorY
        val colDescriptionWidth = 260f
        val colQuantityWidth = 60f
        val colUnitWidth = 100f
        val colTotalWidth = 100f
        // En-têtes
        drawTableHeader(contentStream, startX, tableStartY, colDescriptionWidth, colQuantityWidth, colUnitWidth, colTotalWidth)

        var rowY = tableStartY - 20
        details.lines.foreach { line =>
          drawTableRow(
            contentStream,
            startX,
            rowY,
            colDescriptionWidth,
            colQuantityWidth,
            colUnitWidth,
            colTotalWidth,
            line.description,
            line.quantity.bigDecimal.stripTrailingZeros().toPlainString,
            line.unitPriceHt.bigDecimal.stripTrailingZeros().toPlainString,
            Formatting.formatAmount(line.totalHt)
          )
          rowY -= 20
        }

        // Totaux
        cursorY = rowY - 10
        val totalsX = startX + colDescriptionWidth + colQuantityWidth

        val vatPercent = (details.invoice.vatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        val vatPercentText = vatPercent.bigDecimal.stripTrailingZeros().toPlainString
        val linesTotaux = Seq(
          "Sous-total HT" -> Formatting.formatAmount(totals.subTotal),
          s"TVA (${vatPercentText} %)" -> Formatting.formatAmount(totals.vatAmount),
          "Total TTC" -> Formatting.formatAmount(totals.totalInclTax)
        )

        linesTotaux.foreach { case (label, value) =>
          cursorY -= 18
          contentStream.beginText()
          contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12)
          contentStream.newLineAtOffset(totalsX, cursorY)
          contentStream.showText(label)
          contentStream.endText()

          contentStream.beginText()
          contentStream.setFont(PDType1Font.HELVETICA, 12)
          contentStream.newLineAtOffset(totalsX + colUnitWidth, cursorY)
          contentStream.showText(value)
          contentStream.endText()
        }
      }

      document.save(outputPath.toFile)
    }

    outputPath

  private def drawTableHeader(
      contentStream: PDPageContentStream,
      startX: Float,
      startY: Float,
      colDescriptionWidth: Float,
      colQuantityWidth: Float,
      colUnitWidth: Float,
      colTotalWidth: Float
  ): Unit =
    val headers = Seq(
      "Description" -> colDescriptionWidth,
      "Qté" -> colQuantityWidth,
      "PU HT" -> colUnitWidth,
      "Total HT" -> colTotalWidth
    )

    var cursorX = startX
    headers.foreach { case (label, width) =>
      contentStream.addRect(cursorX, startY - 20, width, 20)
      contentStream.stroke()

      contentStream.beginText()
      contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12)
      contentStream.newLineAtOffset(cursorX + 4, startY - 15)
      contentStream.showText(label)
      contentStream.endText()

      cursorX += width
    }

  private def drawTableRow(
      contentStream: PDPageContentStream,
      startX: Float,
      startY: Float,
      colDescriptionWidth: Float,
      colQuantityWidth: Float,
      colUnitWidth: Float,
      colTotalWidth: Float,
      description: String,
      quantity: String,
      unitPrice: String,
      total: String
  ): Unit =
    val values = Seq(
      description -> colDescriptionWidth,
      quantity -> colQuantityWidth,
      unitPrice -> colUnitWidth,
      total -> colTotalWidth
    )

    var cursorX = startX
    values.foreach { case (text, width) =>
      contentStream.addRect(cursorX, startY - 20, width, 20)
      contentStream.stroke()

      contentStream.beginText()
      contentStream.setFont(PDType1Font.HELVETICA, 11)
      contentStream.newLineAtOffset(cursorX + 4, startY - 15)
      contentStream.showText(text)
      contentStream.endText()

      cursorX += width
    }
