package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.data.*
import com.phuctran.photobooth.desktop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.*
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

private val vnFormat = NumberFormat.getNumberInstance(Locale("vi", "VN"))
private fun money(value: Long) = "${vnFormat.format(value)} ₫"
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val periods = listOf("Hôm nay", "7 ngày", "Tháng này", "Tháng trước")
private data class RevenueData(
    val summary: SalesSummary,
    val points: List<DailyRevenuePoint>,
    val sizes: List<SizeBreakdown>,
    val recent: List<SaleTransaction>
)

@Composable
fun RevenueScreen(salesRepository: SalesRepository, projectDir: Path) {
    var period by remember { mutableIntStateOf(0) }
    val now = LocalDate.now()
    val start = when (period) {
        0 -> now
        1 -> now.minusDays(6)
        2 -> now.withDayOfMonth(1)
        else -> now.withDayOfMonth(1).minusMonths(1)
    }
    val end = when (period) {
        0 -> now.plusDays(1)
        1 -> now.plusDays(1)
        2 -> now.withDayOfMonth(1).plusMonths(1)
        else -> now.withDayOfMonth(1)
    }
    val zone = ZoneId.systemDefault()
    val startTime = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endTime = end.atStartOfDay(zone).toInstant().toEpochMilli()
    var data by remember { mutableStateOf<RevenueData?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(salesRepository, startTime, endTime) {
        data = null
        failure = null
        runCatching {
            withContext(Dispatchers.IO) {
                RevenueData(salesRepository.getSummary(startTime, endTime),
                    salesRepository.getDailyPoints(startTime, endTime),
                    salesRepository.getSizeBreakdowns(startTime, endTime),
                    salesRepository.getRecentTransactions(startTime, endTime))
            }
        }.onSuccess { data = it }.onFailure { failure = it.message ?: "Không thể đọc dữ liệu doanh thu." }
    }

    Column(Modifier.fillMaxSize().background(NeutralBg).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Doanh thu", style = MaterialTheme.typography.h4, color = NeutralText)
                Text("Giao dịch đã hoàn tất · dữ liệu lưu trên máy", style = MaterialTheme.typography.body2, color = NeutralSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEachIndexed { index, label ->
                    val selected = period == index
                    Box(Modifier.background(if (selected) AccentNude else NeutralPanel, RoundedCornerShape(20.dp))
                        .border(1.dp, if (selected) AccentNude else NeutralBorder, RoundedCornerShape(20.dp))
                        .clickable { period = index }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(label, style = MaterialTheme.typography.button,
                            color = if (selected) Color.White else NeutralText)
                    }
                }
            }
        }
        if (failure != null) {
            Text("Không thể tải doanh thu: $failure", color = AccentRed, style = MaterialTheme.typography.body1)
        } else if (data == null) {
            Text("Đang tải dữ liệu…", color = NeutralSecondary, style = MaterialTheme.typography.body1)
        } else {
            val result = data!!
            val summary = result.summary
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Metric("Doanh thu", money(summary.revenue), Modifier.weight(1f), true)
                Metric("Lượt chụp", vnFormat.format(summary.sessions), Modifier.weight(1f))
                Metric("Tổng tấm đã in", vnFormat.format(summary.prints), Modifier.weight(1f))
                Metric("Tiền từ ảnh in thêm", money(summary.extraRevenue), Modifier.weight(1f))
            }
            if (summary.sessions == 0L) {
                SurfacePanel {
                    Text("Chưa có giao dịch trong khoảng thời gian này", style = MaterialTheme.typography.h6, color = NeutralText)
                    Text("Khi một phiên hoàn tất, doanh thu sẽ xuất hiện tại đây.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                }
            } else {
                SurfacePanel {
                    Text("Doanh thu theo ngày", style = MaterialTheme.typography.h6, color = NeutralText)
                    Text("${start.format(DateTimeFormatter.ofPattern("dd/MM"))} – ${end.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                        style = MaterialTheme.typography.body2, color = NeutralSecondary)
                    val days = remember(start, end, result.points) {
                        val lookup = result.points.associateBy { it.date }
                        (0 until java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()).map { offset ->
                            val date = start.plusDays(offset.toLong())
                            date to (lookup[date.toString()]?.revenue ?: 0L)
                        }
                    }
                    val peak = days.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 14.dp)) {
                        val gap = 5.dp.toPx()
                        val barWidth = (size.width / days.size - gap).coerceAtLeast(2f)
                        drawLine(NeutralBorder, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                        days.forEachIndexed { index, (_, amount) ->
                            if (amount > 0) {
                                val height = (amount.toFloat() / peak.toFloat() * (size.height - 8.dp.toPx())).coerceAtLeast(3.dp.toPx())
                                drawRect(if (amount == peak) AccentNudeDark else AccentNude.copy(alpha = .6f),
                                    Offset(index * size.width / days.size + gap / 2f, size.height - height), Size(barWidth, height))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val ticks = minOf(days.size, 5)
                        (0 until ticks).forEach { tick ->
                            val index = if (ticks == 1) 0 else tick * (days.lastIndex) / (ticks - 1)
                            Text(days[index].first.format(DateTimeFormatter.ofPattern("dd/MM")),
                                style = MaterialTheme.typography.caption, color = NeutralSecondary)
                        }
                    }
                    Text("Ngày cao nhất: ${money(peak)}", style = MaterialTheme.typography.caption, color = AccentNudeDark)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("5x15", "10x15").forEach { size ->
                        val item = result.sizes.find { it.printSize == size }
                        SurfacePanel(Modifier.weight(1f)) {
                            Text(size, style = MaterialTheme.typography.h6, color = AccentNudeDark)
                            Text("${item?.sessions ?: 0} lượt · ${item?.prints ?: 0} tấm", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                            Text(money(item?.revenue ?: 0L), style = MaterialTheme.typography.h5, color = NeutralText)
                            Text("Ảnh in thêm: ${money(item?.extraRevenue ?: 0L)}", style = MaterialTheme.typography.body2, color = NeutralSecondary)
                        }
                    }
                }
                SurfacePanel {
                    Text("Hiệu quả ảnh in thêm", style = MaterialTheme.typography.h6, color = NeutralText)
                    Text("${summary.extraPrints} tấm thêm · ${money(summary.extraRevenue)} · ${if (summary.revenue == 0L) 0 else summary.extraRevenue * 100 / summary.revenue}% doanh thu", style = MaterialTheme.typography.body1, color = AccentNudeDark)
                }
                SurfacePanel {
                    Text("Giao dịch gần đây", style = MaterialTheme.typography.h6, color = NeutralText)
                    result.recent.forEach { sale ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("${sale.layoutTitle} · ${sale.printSize}", style = MaterialTheme.typography.body1, color = NeutralText, fontWeight = FontWeight.SemiBold)
                                Text("${Instant.ofEpochMilli(sale.createdAt).atZone(zone).format(dateFormat)} · ${sale.quantity} tấm · ${sale.paymentMethod}",
                                    style = MaterialTheme.typography.body2, color = NeutralSecondary)
                            }
                            Text(money(sale.totalAmount), style = MaterialTheme.typography.body1, color = NeutralText, fontWeight = FontWeight.Bold)
                        }
                        if (sale != result.recent.last()) androidx.compose.material.Divider(color = NeutralBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfacePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(NeutralPanel, RoundedCornerShape(16.dp)).border(1.dp, NeutralBorder, RoundedCornerShape(16.dp))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    SurfacePanel(modifier) {
        Text(label, style = MaterialTheme.typography.body2, color = if (emphasis) AccentNudeDark else NeutralSecondary)
        Text(value, style = MaterialTheme.typography.h5, color = if (emphasis) AccentNudeDark else NeutralText)
    }
}
