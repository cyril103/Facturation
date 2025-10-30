package invoicer
package dao

import invoicer.model.Client
import scala.collection.mutable.ListBuffer
import scala.util.Using

/** Accès aux clients via JDBC. */
object ClientDAO:

  def findAll(): Seq[Client] =
    val sql = "SELECT id, name, address, email, phone, siret FROM clients ORDER BY name"
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.createStatement()) { stmt =>
        Using.resource(stmt.executeQuery(sql)) { rs =>
          val buffer = ListBuffer.empty[Client]
          while rs.next() do
            buffer += Client(
              Option(rs.getInt("id")),
              rs.getString("name"),
              Option(rs.getString("address")).filter(_.nonEmpty),
              Option(rs.getString("email")).filter(_.nonEmpty),
              Option(rs.getString("phone")).filter(_.nonEmpty),
              Option(rs.getString("siret")).filter(_.nonEmpty)
            )
          buffer.toList
        }
      }
    }

  def searchByName(fragment: String): Seq[Client] =
    val sql =
      """SELECT id, name, address, email, phone, siret
        |FROM clients
        |WHERE LOWER(name) LIKE ?
        |ORDER BY name""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, s"%${fragment.toLowerCase.trim}%")
        Using.resource(ps.executeQuery()) { rs =>
          val buffer = ListBuffer.empty[Client]
          while rs.next() do
            buffer += Client(
              Option(rs.getInt("id")),
              rs.getString("name"),
              Option(rs.getString("address")).filter(_.nonEmpty),
              Option(rs.getString("email")).filter(_.nonEmpty),
              Option(rs.getString("phone")).filter(_.nonEmpty),
              Option(rs.getString("siret")).filter(_.nonEmpty)
            )
          buffer.toList
        }
      }
    }

  def insert(client: Client): Client =
    val sql =
      """INSERT INTO clients(name, address, email, phone, siret)
        |VALUES (?, ?, ?, ?, ?)""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, client.name)
        ps.setString(2, client.address.orNull)
        ps.setString(3, client.email.orNull)
        ps.setString(4, client.phone.orNull)
        ps.setString(5, client.siret.orNull)
        ps.executeUpdate()
        val id = Using.resource(conn.createStatement()) { stmt =>
          Using.resource(stmt.executeQuery("SELECT last_insert_rowid()")) { rs =>
            if rs.next() then rs.getInt(1) else 0
          }
        }
        client.copy(id = Option(id))
      }
    }

  def update(client: Client): Unit =
    require(client.id.isDefined, "Client ID requis pour la mise à jour")
    val sql =
      """UPDATE clients SET name = ?, address = ?, email = ?, phone = ?, siret = ?
        |WHERE id = ?""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, client.name)
        ps.setString(2, client.address.orNull)
        ps.setString(3, client.email.orNull)
        ps.setString(4, client.phone.orNull)
        ps.setString(5, client.siret.orNull)
        ps.setInt(6, client.id.get)
        ps.executeUpdate()
      }
    }

  def delete(id: Int): Unit =
    val sql = "DELETE FROM clients WHERE id = ?"
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setInt(1, id)
        ps.executeUpdate()
      }
    }
