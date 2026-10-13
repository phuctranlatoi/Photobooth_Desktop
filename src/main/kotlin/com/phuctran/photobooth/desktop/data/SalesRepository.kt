package com.phuctran.photobooth.desktop.data

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/** Local, snapshot-only ledger. Each query opens its own connection; writes are serialized by SQLite. */
class SalesRepository(projectDir: Path) {
    private val url: String

    init {
        val directory = projectDir.resolve("data")
        Files.createDirectories(directory)
        url = "jdbc:sqlite:${directory.resolve("sales.db").toAbsolutePath()}"
        connection().use { db ->
            db.createStatement().use { it.execute("""CREATE TABLE IF NOT EXISTS sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL UNIQUE,
                booth_id TEXT NOT NULL, layout_id TEXT NOT NULL, layout_title TEXT NOT NULL,
                print_size TEXT NOT NULL, quantity INTEGER NOT NULL, included_quantity INTEGER NOT NULL,
                extra_quantity INTEGER NOT NULL, base_price INTEGER NOT NULL, extra_unit_price INTEGER NOT NULL,
                extra_amount INTEGER NOT NULL, total_amount INTEGER NOT NULL, payment_method TEXT NOT NULL,
                created_at INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'completed')""") }
            db.createStatement().use { it.execute("CREATE INDEX IF NOT EXISTS sales_created_at ON sales(created_at)") }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(url).apply {
        createStatement().use { it.execute("PRAGMA busy_timeout=5000") }
    }

    fun recordSale(transaction: SaleTransaction) {
        require(transaction.sessionId.isNotBlank())
        connection().use { db ->
            db.prepareStatement("""INSERT OR IGNORE INTO sales (session_id, booth_id, layout_id, layout_title,
                print_size, quantity, included_quantity, extra_quantity, base_price, extra_unit_price,
                extra_amount, total_amount, payment_method, created_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""").use { stmt ->
                listOf(transaction.sessionId, transaction.boothId, transaction.layoutId, transaction.layoutTitle,
                    transaction.printSize, transaction.quantity, transaction.includedQuantity, transaction.extraQuantity,
                    transaction.basePrice, transaction.extraUnitPrice, transaction.extraAmount, transaction.totalAmount,
                    transaction.paymentMethod, transaction.createdAt, transaction.status).forEachIndexed { i, value -> stmt.setObject(i + 1, value) }
                stmt.executeUpdate()
            }
        }
    }

    private fun <T> periodQuery(startTime: Long, endTime: Long, sql: String, read: (java.sql.ResultSet) -> T): List<T> =
        connection().use { db ->
            db.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, startTime)
                stmt.setLong(2, endTime)
                stmt.executeQuery().use { rows -> buildList { while (rows.next()) add(read(rows)) } }
            }
        }

    fun getSummary(startTime: Long, endTime: Long): SalesSummary = periodQuery(startTime, endTime,
        """SELECT COALESCE(SUM(total_amount),0), COUNT(*), COALESCE(SUM(quantity),0),
           COALESCE(SUM(extra_amount),0), COALESCE(SUM(extra_quantity),0)
           FROM sales WHERE status='completed' AND created_at >= ? AND created_at < ?""") { r ->
        SalesSummary(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5))
    }.first()

    fun getDailyPoints(startTime: Long, endTime: Long): List<DailyRevenuePoint> = periodQuery(startTime, endTime,
        """SELECT date(created_at / 1000, 'unixepoch', 'localtime'), SUM(total_amount), COUNT(*)
           FROM sales WHERE status='completed' AND created_at >= ? AND created_at < ?
           GROUP BY 1 ORDER BY 1""") { r -> DailyRevenuePoint(r.getString(1), r.getLong(2), r.getLong(3)) }

    fun getSizeBreakdowns(startTime: Long, endTime: Long): List<SizeBreakdown> = periodQuery(startTime, endTime,
        """SELECT print_size, COUNT(*), SUM(quantity), SUM(total_amount), SUM(extra_amount)
           FROM sales WHERE status='completed' AND created_at >= ? AND created_at < ?
           GROUP BY print_size ORDER BY print_size""") { r ->
        SizeBreakdown(r.getString(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5))
    }

    fun getRecentTransactions(limit: Int = 20): List<SaleTransaction> = connection().use { db ->
        db.prepareStatement("SELECT * FROM sales WHERE status='completed' ORDER BY created_at DESC, id DESC LIMIT ?").use { stmt ->
            stmt.setInt(1, limit.coerceIn(0, 1000))
            stmt.executeQuery().use { r -> buildList {
                while (r.next()) add(SaleTransaction(r.getLong("id"), r.getString("session_id"),
                    r.getString("booth_id"), r.getString("layout_id"), r.getString("layout_title"),
                    r.getString("print_size"), r.getInt("quantity"), r.getInt("included_quantity"),
                    r.getInt("extra_quantity"), r.getLong("base_price"), r.getLong("extra_unit_price"),
                    r.getLong("extra_amount"), r.getLong("total_amount"), r.getString("payment_method"),
                    r.getLong("created_at"), r.getString("status")))
            } }
        }
    }

    fun getRecentTransactions(startTime: Long, endTime: Long, limit: Int = 20): List<SaleTransaction> = connection().use { db ->
        db.prepareStatement("SELECT * FROM sales WHERE status='completed' AND created_at >= ? AND created_at < ? ORDER BY created_at DESC, id DESC LIMIT ?").use { stmt ->
            stmt.setLong(1, startTime)
            stmt.setLong(2, endTime)
            stmt.setInt(3, limit.coerceIn(0, 1000))
            stmt.executeQuery().use { r -> buildList {
                while (r.next()) add(SaleTransaction(r.getLong("id"), r.getString("session_id"),
                    r.getString("booth_id"), r.getString("layout_id"), r.getString("layout_title"),
                    r.getString("print_size"), r.getInt("quantity"), r.getInt("included_quantity"),
                    r.getInt("extra_quantity"), r.getLong("base_price"), r.getLong("extra_unit_price"),
                    r.getLong("extra_amount"), r.getLong("total_amount"), r.getString("payment_method"),
                    r.getLong("created_at"), r.getString("status")))
            } }
        }
    }
}
