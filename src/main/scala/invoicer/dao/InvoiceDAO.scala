package invoicer
package dao

import invoicer.model.{Client, Invoice, InvoiceDetails, InvoiceLine}
import java.time.LocalDate
import scala.collection.mutable.ListBuffer
import scala.util.Using

/** Accès aux factures et lignes via JDBC. */
object InvoiceDAO:

  def insert(invoice: Invoice, lines: Seq[InvoiceLine]): Invoice =
    require(lines.nonEmpty, "Une facture doit contenir au moins une ligne")
    Using.resource(Database.connection()) { conn =>
      conn.setAutoCommit(false)
      try
        val insertInvoiceSql =
          """INSERT INTO invoices(number, date, client_id, vat_rate)
            |VALUES (?, ?, ?, ?)""".stripMargin
        val invoiceId = Using.resource(conn.prepareStatement(insertInvoiceSql)) { ps =>
          ps.setString(1, invoice.number)
          ps.setString(2, invoice.date.toString)
          ps.setInt(3, invoice.clientId)
          ps.setBigDecimal(4, invoice.vatRate.bigDecimal)
          ps.executeUpdate()
          Using.resource(conn.createStatement()) { stmt =>
            Using.resource(stmt.executeQuery("SELECT last_insert_rowid()")) { rs =>
              if rs.next() then rs.getInt(1) else throw new IllegalStateException("Insertion facture impossible")
            }
          }
        }

        val insertLineSql =
          """INSERT INTO invoice_lines(invoice_id, item_id, description, quantity, unit_price_ht)
            |VALUES (?, ?, ?, ?, ?)""".stripMargin
        Using.resource(conn.prepareStatement(insertLineSql)) { ps =>
          lines.foreach { line =>
            ps.setInt(1, invoiceId)
            line.itemId match
              case Some(itemId) => ps.setInt(2, itemId)
              case None         => ps.setNull(2, java.sql.Types.INTEGER)
            ps.setString(3, line.description)
            ps.setBigDecimal(4, line.quantity.bigDecimal)
            ps.setBigDecimal(5, line.unitPriceHt.bigDecimal)
            ps.addBatch()
          }
          ps.executeBatch()
        }

        conn.commit()
        invoice.copy(id = Some(invoiceId))
      catch
        case e: Exception =>
          conn.rollback()
          throw e
      finally
        conn.setAutoCommit(true)
    }

  def update(invoice: Invoice, lines: Seq[InvoiceLine]): Invoice =
    require(invoice.id.isDefined, "Impossible de mettre à jour une facture sans identifiant")
    require(lines.nonEmpty, "Une facture doit contenir au moins une ligne")

    val invoiceId = invoice.id.get
    Using.resource(Database.connection()) { conn =>
      conn.setAutoCommit(false)
      try
        val updateInvoiceSql =
          """UPDATE invoices
            |SET number = ?, date = ?, client_id = ?, vat_rate = ?
            |WHERE id = ?""".stripMargin
        Using.resource(conn.prepareStatement(updateInvoiceSql)) { ps =>
          ps.setString(1, invoice.number)
          ps.setString(2, invoice.date.toString)
          ps.setInt(3, invoice.clientId)
          ps.setBigDecimal(4, invoice.vatRate.bigDecimal)
          ps.setInt(5, invoiceId)
          ps.executeUpdate()
        }

        val deleteLinesSql = "DELETE FROM invoice_lines WHERE invoice_id = ?"
        Using.resource(conn.prepareStatement(deleteLinesSql)) { ps =>
          ps.setInt(1, invoiceId)
          ps.executeUpdate()
        }

        val insertLineSql =
          """INSERT INTO invoice_lines(invoice_id, item_id, description, quantity, unit_price_ht)
            |VALUES (?, ?, ?, ?, ?)""".stripMargin
        Using.resource(conn.prepareStatement(insertLineSql)) { ps =>
          lines.foreach { line =>
            ps.setInt(1, invoiceId)
            line.itemId match
              case Some(itemId) => ps.setInt(2, itemId)
              case None         => ps.setNull(2, java.sql.Types.INTEGER)
            ps.setString(3, line.description)
            ps.setBigDecimal(4, line.quantity.bigDecimal)
            ps.setBigDecimal(5, line.unitPriceHt.bigDecimal)
            ps.addBatch()
          }
          ps.executeBatch()
        }

        conn.commit()
        invoice
      catch
        case e: Exception =>
          conn.rollback()
          throw e
      finally
        conn.setAutoCommit(true)
    }

  def listWithClients(): Seq[(Invoice, Client)] =
    val sql =
      """SELECT i.id as invoice_id,
        |       i.number,
        |       i.date,
        |       i.client_id as invoice_client_id,
        |       i.vat_rate,
        |       c.id as client_id,
        |       c.name as client_name,
        |       c.address as client_address,
        |       c.email as client_email,
        |       c.phone as client_phone,
        |       c.siret as client_siret
        |FROM invoices i
        |JOIN clients c ON c.id = i.client_id
        |ORDER BY i.date DESC, i.number DESC""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.createStatement()) { stmt =>
        Using.resource(stmt.executeQuery(sql)) { rs =>
          val buffer = ListBuffer.empty[(Invoice, Client)]
          while rs.next() do
            val invoice = Invoice(
              id = Some(rs.getInt("invoice_id")),
              number = rs.getString("number"),
              date = LocalDate.parse(rs.getString("date")),
              clientId = rs.getInt("invoice_client_id"),
              vatRate = BigDecimal(rs.getString("vat_rate"))
            )
            val client = Client(
              id = Some(rs.getInt("client_id")),
              name = rs.getString("client_name"),
              address = Option(rs.getString("client_address")).filter(_.nonEmpty),
              email = Option(rs.getString("client_email")).filter(_.nonEmpty),
              phone = Option(rs.getString("client_phone")).filter(_.nonEmpty),
              siret = Option(rs.getString("client_siret")).filter(_.nonEmpty)
            )
            buffer += invoice -> client
          buffer.toList
        }
      }
    }

  def findDetails(invoiceId: Int): Option[InvoiceDetails] =
    val invoiceSql =
      """SELECT i.id as invoice_id,
        |       i.number,
        |       i.date,
        |       i.client_id as invoice_client_id,
        |       i.vat_rate,
        |       c.id as client_id,
        |       c.name as client_name,
        |       c.address as client_address,
        |       c.email as client_email,
        |       c.phone as client_phone,
        |       c.siret as client_siret
        |FROM invoices i
        |JOIN clients c ON c.id = i.client_id
        |WHERE i.id = ?""".stripMargin

    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(invoiceSql)) { ps =>
        ps.setInt(1, invoiceId)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then
            val invoice = Invoice(
              id = Some(rs.getInt("invoice_id")),
              number = rs.getString("number"),
              date = LocalDate.parse(rs.getString("date")),
              clientId = rs.getInt("invoice_client_id"),
              vatRate = BigDecimal(rs.getString("vat_rate"))
            )
            val client = Client(
              id = Some(rs.getInt("client_id")),
              name = rs.getString("client_name"),
              address = Option(rs.getString("client_address")).filter(_.nonEmpty),
              email = Option(rs.getString("client_email")).filter(_.nonEmpty),
              phone = Option(rs.getString("client_phone")).filter(_.nonEmpty),
              siret = Option(rs.getString("client_siret")).filter(_.nonEmpty)
            )
            val lines = loadLines(conn, invoiceId)
            Some(InvoiceDetails(invoice, client, lines))
          else None
        }
      }
    }

  private def loadLines(conn: java.sql.Connection, invoiceId: Int): Seq[InvoiceLine] =
    val sql =
      """SELECT id, invoice_id, item_id, description, quantity, unit_price_ht
        |FROM invoice_lines
        |WHERE invoice_id = ?
        |ORDER BY id""".stripMargin
    Using.resource(conn.prepareStatement(sql)) { ps =>
      ps.setInt(1, invoiceId)
      Using.resource(ps.executeQuery()) { rs =>
        val buffer = ListBuffer.empty[InvoiceLine]
        while rs.next() do
          val itemId = rs.getInt("item_id") match
            case 0 if rs.wasNull() => None
            case other             => Some(other)
          buffer += InvoiceLine(
            id = Some(rs.getInt("id")),
            invoiceId = rs.getInt("invoice_id"),
            itemId = itemId,
            description = rs.getString("description"),
            quantity = BigDecimal(rs.getString("quantity")),
            unitPriceHt = BigDecimal(rs.getString("unit_price_ht"))
          )
        buffer.toList
      }
    }
