package invoicer

import java.nio.file.{Files, Path, Paths}
import java.sql.{Connection, DriverManager, Statement}
import scala.util.Using

/** Gestion centralisée de la base SQLite et des migrations. */
object Database:
  private val dbDirectory: Path =
    Paths.get(System.getProperty("user.home"), "app")
  if !Files.exists(dbDirectory) then
    Files.createDirectories(dbDirectory)

  private val dbFile: Path = dbDirectory.resolve("invoicer.db")
  private val url: String = s"jdbc:sqlite:${dbFile.toAbsolutePath}"

  Class.forName("org.sqlite.JDBC")
  migrate()

  /** Obtient une connexion SQLite avec les contraintes d'intégrité activées. */
  def connection(): Connection =
    val conn = DriverManager.getConnection(url)
    Using.resource(conn.createStatement()) { stmt =>
      stmt.execute("PRAGMA foreign_keys = ON")
    }
    conn

  /** Crée les tables si nécessaire et insère les données par défaut. */
  private def migrate(): Unit =
    Using.resource(connection()) { conn =>
      Using.resource(conn.createStatement()) { stmt =>
        createTables(stmt)
        ensureDefaultVat(conn)
      }
    }

  private def createTables(stmt: Statement): Unit =
    stmt.executeUpdate("""
      |CREATE TABLE IF NOT EXISTS company(
      |  id INTEGER PRIMARY KEY AUTOINCREMENT,
      |  name TEXT NOT NULL,
      |  address TEXT,
      |  email TEXT,
      |  phone TEXT,
      |  siret TEXT
      |);
      |""".stripMargin)

    stmt.executeUpdate("""
      |CREATE TABLE IF NOT EXISTS clients(
      |  id INTEGER PRIMARY KEY AUTOINCREMENT,
      |  name TEXT NOT NULL,
      |  address TEXT,
      |  email TEXT,
      |  phone TEXT,
      |  siret TEXT
      |);
      |""".stripMargin)

    stmt.executeUpdate("""
      |CREATE TABLE IF NOT EXISTS items(
      |  id INTEGER PRIMARY KEY AUTOINCREMENT,
      |  name TEXT NOT NULL,
      |  unit_price_ht REAL NOT NULL,
      |  description TEXT
      |);
      |""".stripMargin)

    stmt.executeUpdate("""
      |CREATE TABLE IF NOT EXISTS invoices(
      |  id INTEGER PRIMARY KEY AUTOINCREMENT,
      |  number TEXT NOT NULL UNIQUE,
      |  date TEXT NOT NULL,
      |  client_id INTEGER NOT NULL,
      |  vat_rate REAL NOT NULL,
      |  FOREIGN KEY(client_id) REFERENCES clients(id)
      |);
      |""".stripMargin)

    stmt.executeUpdate("""
      |CREATE TABLE IF NOT EXISTS invoice_lines(
      |  id INTEGER PRIMARY KEY AUTOINCREMENT,
      |  invoice_id INTEGER NOT NULL,
      |  item_id INTEGER,
      |  description TEXT NOT NULL,
      |  quantity REAL NOT NULL,
      |  unit_price_ht REAL NOT NULL,
      |  FOREIGN KEY(invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
      |  FOREIGN KEY(item_id) REFERENCES items(id)
      |);
      |""".stripMargin)

    stmt.executeUpdate("""
      |CREATE TABLE IF NOT EXISTS settings(
      |  key TEXT PRIMARY KEY,
      |  value TEXT
      |);
      |""".stripMargin)

  private def ensureDefaultVat(conn: Connection): Unit =
    val selectSql = "SELECT value FROM settings WHERE key = ?"
    val insertSql = "INSERT INTO settings(key, value) VALUES(?, ?)"
    Using.resource(conn.prepareStatement(selectSql)) { ps =>
      ps.setString(1, "vat_rate")
      val rs = ps.executeQuery()
      if !rs.next() then
        rs.close()
        Using.resource(conn.prepareStatement(insertSql)) { insert =>
          insert.setString(1, "vat_rate")
          insert.setString(2, "0.20")
          insert.executeUpdate()
        }
      else rs.close()
    }
