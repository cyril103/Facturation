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

/** Generation de PDF a partir d'une facture. */
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
      val company = companyService.load()
      var currentPage = new PDPage(PDRectangle.A4)
      document.addPage(currentPage)

      val margin = 50f
      val startX = margin
      var cursorY = currentPage.getMediaBox.getHeight - margin
      var contentStream = new PDPageContentStream(document, currentPage)

      def closeStream(): Unit =
        if contentStream != null then contentStream.close()

      def newPage(): Unit =
        closeStream()
        currentPage = new PDPage(PDRectangle.A4)
        document.addPage(currentPage)
        contentStream = new PDPageContentStream(document, currentPage)
        cursorY = currentPage.getMediaBox.getHeight - margin

      def ensureSpace(height: Float): Unit =
        if cursorY - height < margin then newPage()

      def addVerticalSpace(space: Float): Unit =
        if space > 0 then
          if cursorY - space < margin then newPage()
          cursorY -= space

      val colDescriptionWidth = 260f
      val colQuantityWidth = 60f
      val colUnitWidth = 100f
      val colTotalWidth = 100f
      val rowHeight = 20f

      def optionalLines(label: String, value: Option[String]): Seq[String] =
        value.flatMap(v => Option(v.trim).filter(_.nonEmpty)) match
          case Some(v) =>
            val parts = v.split("\\r?\\n").toSeq.map(_.trim).filter(_.nonEmpty)
            if parts.isEmpty then Seq(s"$label : -")
            else
              val indent = " " * (label.length + 3)
              val first = s"$label : ${parts.head}"
              val rest = parts.tail.map(part => s"$indent$part")
              first +: rest
          case None =>
            Seq(s"$label : -")

      def writeLine(text: String, font: PDType1Font, size: Float, leading: Float): Unit =
        ensureSpace(leading)
        contentStream.beginText()
        contentStream.setFont(font, size)
        contentStream.newLineAtOffset(startX, cursorY)
        contentStream.showText(text)
        contentStream.endText()
        cursorY -= leading

      def drawTableHeader(): Unit =
        ensureSpace(rowHeight)
        val headerY = cursorY
        var cursorX = startX
        val headers = Seq(
          "Description" -> colDescriptionWidth,
          "Qte" -> colQuantityWidth,
          "PU HT" -> colUnitWidth,
          "Total HT" -> colTotalWidth
        )
        headers.foreach { case (label, width) =>
          contentStream.addRect(cursorX, headerY - rowHeight, width, rowHeight)
          contentStream.stroke()

          contentStream.beginText()
          contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12)
          contentStream.newLineAtOffset(cursorX + 4, headerY - 15)
          contentStream.showText(label)
          contentStream.endText()

          cursorX += width
        }
        cursorY -= rowHeight

      def drawTableRow(
          description: String,
          quantity: String,
          unitPrice: String,
          total: String
      ): Unit =
        if cursorY - rowHeight < margin then
          newPage()
          drawTableHeader()
        val rowY = cursorY
        val values = Seq(
          description -> colDescriptionWidth,
          quantity -> colQuantityWidth,
          unitPrice -> colUnitWidth,
          total -> colTotalWidth
        )
        var cursorX = startX
        values.foreach { case (text, width) =>
          contentStream.addRect(cursorX, rowY - rowHeight, width, rowHeight)
          contentStream.stroke()

          contentStream.beginText()
          contentStream.setFont(PDType1Font.HELVETICA, 11)
          contentStream.newLineAtOffset(cursorX + 4, rowY - 15)
          contentStream.showText(text)
          contentStream.endText()

          cursorX += width
        }
        cursorY -= rowHeight

      try
        writeLine(company.name, PDType1Font.HELVETICA_BOLD, 14, 18f)
        val companyInfo =
          optionalLines("Adresse", company.address) ++
            optionalLines("Email", company.email) ++
            optionalLines("Telephone", company.phone) ++
            optionalLines("SIRET", company.siret)
        companyInfo.foreach(line => writeLine(line, PDType1Font.HELVETICA, 12, 14f))

        addVerticalSpace(18f)

        writeLine(s"FACTURE ${details.invoice.number}", PDType1Font.HELVETICA_BOLD, 18, 24f)
        writeLine(s"Date : ${details.invoice.date}", PDType1Font.HELVETICA, 12, 16f)

        addVerticalSpace(16f)

        writeLine("Client", PDType1Font.HELVETICA_BOLD, 13, 18f)
        val clientInfo =
          optionalLines("Nom", Some(details.client.name)) ++
            optionalLines("Adresse", details.client.address) ++
            optionalLines("Email", details.client.email) ++
            optionalLines("Telephone", details.client.phone) ++
            optionalLines("SIRET", details.client.siret)
        clientInfo.foreach(line => writeLine(line, PDType1Font.HELVETICA, 12, 16f))

        addVerticalSpace(18f)

        // Tableau des lignes
        drawTableHeader()
        details.lines.foreach { line =>
          drawTableRow(
            line.description,
            line.quantity.bigDecimal.stripTrailingZeros().toPlainString,
            line.unitPriceHt.bigDecimal.stripTrailingZeros().toPlainString,
            Formatting.formatAmount(line.totalHt)
          )
        }

        addVerticalSpace(10f)
        val totalsX = startX + colDescriptionWidth + colQuantityWidth

        val vatPercent = (details.invoice.vatRate * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        val vatPercentText = vatPercent.bigDecimal.stripTrailingZeros().toPlainString
        val linesTotaux = Seq(
          "Sous-total HT" -> Formatting.formatAmount(totals.subTotal),
          s"TVA (${vatPercentText} %)" -> Formatting.formatAmount(totals.vatAmount),
          "Total TTC" -> Formatting.formatAmount(totals.totalInclTax)
        )

        linesTotaux.foreach { case (label, value) =>
          ensureSpace(18f)
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
          cursorY -= 18
        }
      finally
        closeStream()

      document.save(outputPath.toFile)
    }

    outputPath
