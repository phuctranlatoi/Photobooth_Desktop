package com.phuctran.photobooth.desktop.data

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import com.phuctran.photobooth.desktop.remote.DesktopRevenueClient
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig

/** Local, snapshot-only ledger. Each query opens its own connection; writes are serialized by SQLite. */
class SalesRepository(projectDir: Path) {
    private val url: String
    private val syncLock = Any()

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
            val columns = db.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info(sales)").use { rows ->
                    buildSet { while (rows.next()) add(rows.getString("name")) }
                }
            }
            mapOf("sync_status" to "TEXT NOT NULL DEFAULT 'PENDING'", "original_quantity" to "INTEGER",
                "original_total_amount" to "INTEGER", "adjust_reason" to "TEXT", "adjusted_at" to "INTEGER")
                .forEach { (name, type) -> if (name !in columns) db.createStatement().use {
                    it.execute("ALTER TABLE sales ADD COLUMN $name $type")
                } }
            db.createStatement().use {
                it.executeUpdate("UPDATE sales SET original_quantity=quantity WHERE original_quantity IS NULL")
                it.executeUpdate("UPDATE sales SET original_total_amount=total_amount WHERE original_total_amount IS NULL")
            }
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
                extra_amount, total_amount, payment_method, created_at, status,
                original_quantity, original_total_amount, adjust_reason, adjusted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""").use { stmt ->
                listOf(transaction.sessionId, transaction.boothId, transaction.layoutId, transaction.layoutTitle,
                    transaction.printSize, transaction.quantity, transaction.includedQuantity, transaction.extraQuantity,
                    transaction.basePrice, transaction.extraUnitPrice, transaction.extraAmount, transaction.totalAmount,
                    transaction.paymentMethod, transaction.createdAt, transaction.status,
                    transaction.originalQuantity ?: transaction.quantity, transaction.originalTotalAmount ?: transaction.totalAmount,
                    transaction.adjustReason, transaction.adjustedAt).forEachIndexed { i, value -> stmt.setObject(i + 1, value) }
                stmt.executeUpdate()
            }
        }
    }

    fun adjustTransaction(sessionId: String, actualQuantity: Int, reason: String?): Boolean {
        require(actualQuantity >= 2)
        return connection().use { db ->
            db.autoCommit = false
            try {
                val snapshot = db.prepareStatement("SELECT base_price, included_quantity, extra_unit_price FROM sales WHERE session_id=? AND status='completed'").use { stmt ->
                    stmt.setString(1, sessionId)
                    stmt.executeQuery().use { r -> if (r.next()) Triple(r.getLong(1), r.getInt(2), r.getLong(3)) else null }
                } ?: return@use false
                val extra = maxOf(0, actualQuantity - snapshot.second.coerceAtLeast(2))
                val extraAmount = Math.multiplyExact(extra.toLong(), snapshot.third)
                val total = Math.addExact(snapshot.first, extraAmount)
                val updated = db.prepareStatement("""UPDATE sales SET quantity=?, extra_quantity=?, extra_amount=?, total_amount=?,
                    original_quantity=COALESCE(original_quantity, quantity), original_total_amount=COALESCE(original_total_amount, total_amount),
                    adjust_reason=?, adjusted_at=?, sync_status='PENDING' WHERE session_id=? AND status='completed'""").use { stmt ->
                    listOf(actualQuantity, extra, extraAmount, total, reason, System.currentTimeMillis(), sessionId)
                        .forEachIndexed { i, value -> stmt.setObject(i + 1, value) }
                    stmt.executeUpdate() > 0
                }
                db.commit()
                updated
            } catch (e: Exception) { db.rollback(); throw e }
        }
    }

    fun voidTransaction(sessionId: String, reason: String?): Boolean = connection().use { db ->
        db.prepareStatement("UPDATE sales SET status='VOIDED', adjust_reason=?, adjusted_at=?, sync_status='PENDING' WHERE session_id=? AND status='completed'").use { stmt ->
            stmt.setString(1, reason)
            stmt.setLong(2, System.currentTimeMillis())
            stmt.setString(3, sessionId)
            stmt.executeUpdate() > 0
        }
    }

    private fun readSale(r: java.sql.ResultSet) = SaleTransaction(r.getLong("id"), r.getString("session_id"),
        r.getString("booth_id"), r.getString("layout_id"), r.getString("layout_title"), r.getString("print_size"),
        r.getInt("quantity"), r.getInt("included_quantity"), r.getInt("extra_quantity"), r.getLong("base_price"),
        r.getLong("extra_unit_price"), r.getLong("extra_amount"), r.getLong("total_amount"), r.getString("payment_method"),
        r.getLong("created_at"), r.getString("status"), r.getInt("original_quantity").takeUnless { r.wasNull() },
        r.getLong("original_total_amount").takeUnless { r.wasNull() }, r.getString("adjust_reason"),
        r.getLong("adjusted_at").takeUnless { r.wasNull() })

    fun syncPendingSales(client: DesktopRevenueClient, config: DesktopBoothConfig) {
        if (config.neonDatabaseUrl.isBlank() && config.boothApiKey.isBlank()) return
        synchronized(syncLock) {
        connection().use { db ->
            val pending = db.prepareStatement("SELECT * FROM sales WHERE sync_status='PENDING' ORDER BY id").use { stmt ->
                stmt.executeQuery().use { r -> buildList {
                    while (r.next()) add(readSale(r))
                } }
            }
            for (sale in pending) {
                val synced = when {
                    sale.isVoided -> client.recordTransaction(config, sale) && client.voidTransaction(config, sale.sessionId, sale.adjustReason)
                    sale.isAdjusted -> client.recordTransaction(config, sale) && client.adjustTransaction(config, sale.sessionId, sale.quantity, sale.adjustReason)
                    else -> client.recordTransaction(config, sale)
                }
                if (!synced) break
                db.prepareStatement("UPDATE sales SET sync_status='SYNCED' WHERE id=? AND sync_status='PENDING' AND quantity=? AND status=? AND adjusted_at IS ?").use {
                    it.setLong(1, sale.id)
                    it.setInt(2, sale.quantity)
                    it.setString(3, sale.status)
                    it.setObject(4, sale.adjustedAt)
                    it.executeUpdate()
                }
            }
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
        db.prepareStatement("SELECT * FROM sales WHERE status IN ('completed','VOIDED','voided') ORDER BY created_at DESC, id DESC LIMIT ?").use { stmt ->
            stmt.setInt(1, limit.coerceIn(0, 1000))
            stmt.executeQuery().use { r -> buildList {
                while (r.next()) add(readSale(r))
            } }
        }
    }

    fun getRecentTransactions(startTime: Long, endTime: Long, limit: Int = 20): List<SaleTransaction> = connection().use { db ->
        db.prepareStatement("SELECT * FROM sales WHERE status IN ('completed','VOIDED','voided') AND created_at >= ? AND created_at < ? ORDER BY created_at DESC, id DESC LIMIT ?").use { stmt ->
            stmt.setLong(1, startTime)
            stmt.setLong(2, endTime)
            stmt.setInt(3, limit.coerceIn(0, 1000))
            stmt.executeQuery().use { r -> buildList {
                while (r.next()) add(readSale(r))
            } }
        }
    }
}
