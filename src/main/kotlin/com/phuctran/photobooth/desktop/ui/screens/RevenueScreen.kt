package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.config.DesktopBoothConfig
import com.phuctran.photobooth.desktop.data.*
import com.phuctran.photobooth.desktop.remote.DesktopRevenueClient
import com.phuctran.photobooth.desktop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale("vi", "VN"))
private fun vnd(value: Long) = "${numberFormat.format(value)} ₫"
private data class RevenueData(val summary: SalesSummary, val points: List<DailyRevenuePoint>,
    val sizes: List<SizeBreakdown>, val recent: List<SaleTransaction>, val cloud: Boolean)

@Composable
fun RevenueScreen(salesRepository: SalesRepository, revenueClient: DesktopRevenueClient, config: DesktopBoothConfig) {
    var period by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val now = LocalDate.now()
    val start = when (period) {
        0 -> now
        1 -> now.minusDays(6)
        2 -> now.withDayOfMonth(1)
        else -> now.withDayOfMonth(1).minusMonths(1)
    }
    val end = if (period == 3) now.withDayOfMonth(1) else if (period == 2) now.withDayOfMonth(1).plusMonths(1) else now.plusDays(1)
    val zone = ZoneId.systemDefault()
    val startTime = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endTime = end.atStartOfDay(zone).toInstant().toEpochMilli()
    var data by remember { mutableStateOf<RevenueData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(salesRepository, revenueClient, config, startTime, endTime, refresh) {
        loading = true
        data = null
        failure = null
        runCatching {
            withContext(Dispatchers.IO) {
                salesRepository.syncPendingSales(revenueClient, config)
                val cloud = revenueClient.getDashboard(config, startTime, endTime)
                val recent = if (cloud != null) revenueClient.getRecentTransactions(config, startTime, endTime) else null
                if (cloud != null && recent != null) RevenueData(cloud.first, cloud.second, cloud.third, recent, true)
                else RevenueData(salesRepository.getSummary(startTime, endTime), salesRepository.getDailyPoints(startTime, endTime),
                    salesRepository.getSizeBreakdowns(startTime, endTime), salesRepository.getRecentTransactions(startTime, endTime), false)
            }
        }.onSuccess { data = it }.onFailure { failure = it.message ?: "Không thể tải dữ liệu doanh thu." }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(NeutralBg).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Doanh thu", style = MaterialTheme.typography.h4, color = NeutralText)
                Text("Tổng quan hoạt động và lịch sử giao dịch", style = MaterialTheme.typography.body2, color = NeutralSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (data?.cloud == true) "● Đồng bộ đám mây (Neon)" else "● Ngoại tuyến · Dữ liệu trên máy này",
                    style = MaterialTheme.typography.body2, color = if (data?.cloud == true) AccentMint else AccentAmber)
                OutlinedButton(onClick = { refresh++ }, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Làm mới")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Hôm nay", "7 ngày", "Tháng này", "Tháng trước").forEachIndexed { index, label ->
                val selected = index == period
                Box(Modifier.background(if (selected) AccentNude else NeutralPanel, RoundedCornerShape(22.dp))
                    .border(1.dp, if (selected) AccentNude else NeutralBorder, RoundedCornerShape(22.dp))
                    .clickable { period = index }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(label, style = MaterialTheme.typography.button, color = if (selected) Color.White else NeutralText)
                }
            }
        }
        if (failure != null) {
            Text("Không thể tải doanh thu: $failure · Hãy thử làm mới.", color = AccentRed)
        } else if (loading && data == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Đang tải dữ liệu doanh thu…", color = NeutralSecondary)
            }
        } else if (data != null) {
            val result = data!!
            val summary = result.summary
            val metrics = listOf(
                Triple("DOANH THU", vnd(summary.revenue), true),
                Triple("LƯỢT CHỤP", numberFormat.format(summary.sessions), false),
                Triple("TỔNG TẤM ĐÃ IN", numberFormat.format(summary.prints), false),
                Triple("ẢNH IN THÊM", numberFormat.format(summary.extraPrints), false),
                Triple("DOANH THU IN THÊM", vnd(summary.extraRevenue), false),
                Triple("TRUNG BÌNH / LƯỢT", vnd(if (summary.sessions == 0L) 0L else summary.revenue / summary.sessions), false)
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = when {
                    maxWidth >= 750.dp -> 3
                    maxWidth >= 490.dp -> 2
                    else -> 1
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (label, value, hero) ->
                                Metric(label, value, Modifier.weight(1f), hero)
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (summary.sessions == 0L) {
                Panel {
                    Text("Chưa có giao dịch trong khoảng thời gian này", style = MaterialTheme.typography.h6)
                    Text("Các phiên đã in thành công sẽ xuất hiện tại đây.", color = NeutralSecondary)
                }
            } else {
                Panel {
                    Text("Doanh thu theo ngày", style = MaterialTheme.typography.h6)
                    val days = remember(start, end, result.points) {
                        val lookup = result.points.associateBy { it.date }
                        (0 until java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()).map { offset ->
                            val date = start.plusDays(offset.toLong())
                            date to (lookup[date.toString()]?.revenue ?: 0L)
                        }
                    }
                    val peak = days.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 12.dp)) {
                        if (days.isNotEmpty()) {
                            val gap = 5.dp.toPx()
                            val width = size.width / days.size
                            drawLine(NeutralBorder, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                            days.forEachIndexed { index, (_, amount) ->
                                if (amount > 0) {
                                    val height = (amount.toFloat() / peak.toFloat() * (size.height - 8.dp.toPx())).coerceAtLeast(3.dp.toPx())
                                    drawRect(if (amount == peak) AccentNudeDark else AccentNude.copy(alpha = .65f),
                                        Offset(index * width + gap / 2, size.height - height), Size((width - gap).coerceAtLeast(2f), height))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val ticks = minOf(days.size, 5)
                        repeat(ticks) { tick ->
                            val index = if (ticks == 1) 0 else tick * days.lastIndex / (ticks - 1)
                            Text(days[index].first.format(DateTimeFormatter.ofPattern("dd/MM")), style = MaterialTheme.typography.caption, color = NeutralSecondary)
                        }
                    }
                    Text("Ngày cao nhất: ${vnd(peak)}", style = MaterialTheme.typography.caption, color = AccentNudeDark)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("5x15", "10x15").forEach { size ->
                        val item = result.sizes.find { it.printSize.replace(" ", "").equals(size, true) }
                        Panel(Modifier.weight(1f)) {
                            Text(size, style = MaterialTheme.typography.h6, color = AccentNudeDark)
                            Text("${item?.sessions ?: 0} lượt · ${item?.prints ?: 0} tấm", color = NeutralSecondary)
                            Text(vnd(item?.revenue ?: 0), style = MaterialTheme.typography.h5, color = NeutralText)
                            Text("Doanh thu in thêm: ${vnd(item?.extraRevenue ?: 0)}", color = NeutralSecondary)
                        }
                    }
                }
                Panel {
                    Text("Hiệu quả ảnh in thêm", style = MaterialTheme.typography.h6)
                    Text("${summary.extraPrints} tấm thêm · ${vnd(summary.extraRevenue)} · ${if (summary.revenue == 0L) 0 else summary.extraRevenue * 100 / summary.revenue}% doanh thu",
                        color = AccentNudeDark)
                }
                Panel {
                    Text("Giao dịch gần đây", style = MaterialTheme.typography.h6)
                    result.recent.forEach { sale ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${sale.layoutTitle} · ${sale.printSize}", fontWeight = FontWeight.SemiBold)
                                Text("${Instant.ofEpochMilli(sale.createdAt).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))} · ${sale.quantity} tấm · ${sale.paymentMethod}",
                                    style = MaterialTheme.typography.body2, color = NeutralSecondary)
                            }
                            Text(vnd(sale.totalAmount), fontWeight = FontWeight.Bold)
                        }
                        Divider(color = NeutralBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(NeutralPanel, RoundedCornerShape(16.dp)).border(1.dp, NeutralBorder, RoundedCornerShape(16.dp))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, hero: Boolean = false) {
    Column(modifier.background(if (hero) AccentNude else NeutralPanel, RoundedCornerShape(16.dp))
        .then(if (hero) Modifier else Modifier.border(1.dp, NeutralBorder, RoundedCornerShape(16.dp)))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.body2, color = if (hero) Color.White else NeutralSecondary,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.h5, color = if (hero) Color.White else NeutralText,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
