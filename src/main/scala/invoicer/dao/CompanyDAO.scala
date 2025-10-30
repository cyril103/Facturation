package invoicer
package dao

import invoicer.model.Company
import scala.util.Using

/** Gestion des informations de l'entreprise. */
object CompanyDAO:

  def findCurrent(): Option[Company] =
    val sql =
      """SELECT id, name, address, email, phone, siret
        |FROM company
        |ORDER BY id DESC
        |LIMIT 1""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.createStatement()) { stmt =>
        Using.resource(stmt.executeQuery(sql)) { rs =>
          if rs.next() then
            Some(
              Company(
                Option(rs.getInt("id")),
                rs.getString("name"),
                Option(rs.getString("address")).filter(_.nonEmpty),
                Option(rs.getString("email")).filter(_.nonEmpty),
                Option(rs.getString("phone")).filter(_.nonEmpty),
                Option(rs.getString("siret")).filter(_.nonEmpty)
              )
            )
          else None
        }
      }
    }

  def save(company: Company): Company =
    company.id match
      case Some(_) =>
        update(company)
        company
      case None =>
        findCurrent() match
          case Some(existing) =>
            val updated = company.copy(id = existing.id)
            update(updated)
            updated
          case None => insert(company)

  private def insert(company: Company): Company =
    val sql =
      """INSERT INTO company(name, address, email, phone, siret)
        |VALUES (?, ?, ?, ?, ?)""".stripMargin
    Using.resource(Database.connection()) { conn =>
      val generatedId =
        Using.resource(conn.prepareStatement(sql)) { ps =>
          ps.setString(1, company.name)
          ps.setString(2, company.address.orNull)
          ps.setString(3, company.email.orNull)
          ps.setString(4, company.phone.orNull)
          ps.setString(5, company.siret.orNull)
          ps.executeUpdate()
          Using.resource(conn.createStatement()) { stmt =>
            Using.resource(stmt.executeQuery("SELECT last_insert_rowid()")) { rs =>
              if rs.next() then rs.getInt(1) else 0
            }
          }
        }
      company.copy(id = Option(generatedId))
    }

  private def update(company: Company): Unit =
    require(company.id.isDefined, "ID nécessaire pour la mise à jour de l'entreprise")
    val sql =
      """UPDATE company
        |SET name = ?, address = ?, email = ?, phone = ?, siret = ?
        |WHERE id = ?""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, company.name)
        ps.setString(2, company.address.orNull)
        ps.setString(3, company.email.orNull)
        ps.setString(4, company.phone.orNull)
        ps.setString(5, company.siret.orNull)
        ps.setInt(6, company.id.get)
        ps.executeUpdate()
      }
    }
