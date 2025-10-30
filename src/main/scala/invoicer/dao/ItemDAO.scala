package invoicer
package dao

import invoicer.model.Item
import scala.collection.mutable.ListBuffer
import scala.util.Using

/** Accès aux données des articles/prestations. */
object ItemDAO:

  def findAll(): Seq[Item] =
    val sql = "SELECT id, name, unit_price_ht, description FROM items ORDER BY name"
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.createStatement()) { stmt =>
        Using.resource(stmt.executeQuery(sql)) { rs =>
          val buffer = ListBuffer.empty[Item]
          while rs.next() do
            val price = Option(rs.getString("unit_price_ht")).filter(_.nonEmpty).map(BigDecimal(_)).getOrElse(BigDecimal(0))
            buffer += Item(
              Option(rs.getInt("id")),
              rs.getString("name"),
              price,
              Option(rs.getString("description")).filter(_.nonEmpty)
            )
          buffer.toList
        }
      }
    }

  def searchByName(fragment: String): Seq[Item] =
    val sql =
      """SELECT id, name, unit_price_ht, description
        |FROM items
        |WHERE LOWER(name) LIKE ?
        |ORDER BY name""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, s"%${fragment.toLowerCase.trim}%")
        Using.resource(ps.executeQuery()) { rs =>
          val buffer = ListBuffer.empty[Item]
          while rs.next() do
            val price = Option(rs.getString("unit_price_ht")).filter(_.nonEmpty).map(BigDecimal(_)).getOrElse(BigDecimal(0))
            buffer += Item(
              Option(rs.getInt("id")),
              rs.getString("name"),
              price,
              Option(rs.getString("description")).filter(_.nonEmpty)
            )
          buffer.toList
        }
      }
    }

  def insert(item: Item): Item =
    val sql = "INSERT INTO items(name, unit_price_ht, description) VALUES (?, ?, ?)"
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, item.name)
        ps.setBigDecimal(2, item.unitPriceHt.bigDecimal)
        ps.setString(3, item.description.orNull)
        ps.executeUpdate()
        val id = Using.resource(conn.createStatement()) { stmt =>
          Using.resource(stmt.executeQuery("SELECT last_insert_rowid()")) { rs =>
            if rs.next() then rs.getInt(1) else 0
          }
        }
        item.copy(id = Option(id))
      }
    }

  def update(item: Item): Unit =
    require(item.id.isDefined, "Item ID requis pour la mise à jour")
    val sql =
      """UPDATE items SET name = ?, unit_price_ht = ?, description = ?
        |WHERE id = ?""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, item.name)
        ps.setBigDecimal(2, item.unitPriceHt.bigDecimal)
        ps.setString(3, item.description.orNull)
        ps.setInt(4, item.id.get)
        ps.executeUpdate()
      }
    }

  def delete(id: Int): Unit =
    val sql = "DELETE FROM items WHERE id = ?"
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setInt(1, id)
        ps.executeUpdate()
      }
    }
