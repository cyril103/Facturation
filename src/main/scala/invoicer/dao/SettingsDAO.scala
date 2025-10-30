package invoicer
package dao

import scala.util.Using

/** Accès simplifié à la table des paramètres. */
object SettingsDAO:

  def get(key: String): Option[String] =
    val sql = "SELECT value FROM settings WHERE key = ?"
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, key)
        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then Option(rs.getString("value")) else None
        }
      }
    }

  def upsert(key: String, value: String): Unit =
    val sql =
      """INSERT INTO settings(key, value)
        |VALUES (?, ?)
        |ON CONFLICT(key) DO UPDATE SET value = excluded.value""".stripMargin
    Using.resource(Database.connection()) { conn =>
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, key)
        ps.setString(2, value)
        ps.executeUpdate()
      }
    }

  def vatRate(): BigDecimal =
    get("vat_rate").flatMap(s => scala.util.Try(BigDecimal(s)).toOption).getOrElse(BigDecimal("0.20"))

  def updateVatRate(rate: BigDecimal): Unit =
    upsert("vat_rate", rate.bigDecimal.stripTrailingZeros().toPlainString)
