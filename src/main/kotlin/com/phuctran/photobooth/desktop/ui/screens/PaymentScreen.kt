package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PaymentScreen(
    totalAmount: Long,
    layout: LayoutMode,
    effect: EffectMode,
    printCopies: Int,
    paymentQrData: String?,
    isPaymentConfigured: Boolean,
    staffCashPin: String = "1234",
    onPaid: () -> Unit,
    onBack: () -> Unit
) {
    var showStaffPinDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopStart).padding(24.dp)) {
            CompactLocalHeader(
                step = "Bước 3",
                title = "Thanh toán",
                onBack = onBack
            )
            Text("Quét QR bằng ứng dụng ngân hàng hoặc nhờ nhân viên xác nhận tiền mặt.", style = MaterialTheme.typography.body2, color = NeutralSecondary)
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(start = 68.dp, end = 68.dp, top = 116.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text("Số tiền cần thanh toán", style = MaterialTheme.typography.h5, color = NeutralText)
                Spacer(Modifier.height(12.dp))
                Text(formatVnd(totalAmount), style = MaterialTheme.typography.h1.copy(fontSize = 44.sp), fontWeight = FontWeight.Black, color = AccentNudeDark)
                Spacer(Modifier.height(32.dp))
                OutputRow("Bố cục", layout.title)
                Spacer(Modifier.height(10.dp))
                OutputRow("Màu ảnh", effect.title)
                Spacer(Modifier.height(10.dp))
                OutputRow("Bản in", "$printCopies bản")
                Spacer(Modifier.height(24.dp))
                StatusChip(
                    label = "Thanh toán QR",
                    value = when {
                        !isPaymentConfigured -> "Chưa sẵn sàng"
                        paymentQrData == "ERROR" -> "Không thể tạo mã"
                        paymentQrData == null -> "Đang tạo mã"
                        else -> "Sẵn sàng"
                    },
                    color = if (isPaymentConfigured && paymentQrData != "ERROR") AccentMint else AccentAmber
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .liquidGlassPlate(
                        variant = LiquidGlassPlateVariant.Elevated,
                        cornerRadius = 16.dp,
                        elevation = LiquidGlassPlateMetrics.elevatedElevation
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isPaymentConfigured) {
                    if (paymentQrData == null) {
                        CircularProgressIndicator(color = AccentNude, strokeWidth = 5.dp, modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Đang tạo mã thanh toán...", color = NeutralSecondary, style = MaterialTheme.typography.subtitle1)
                    } else if (paymentQrData == "ERROR") {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = AccentAmber,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Không tạo được mã QR", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = AccentAmber)
                        Spacer(Modifier.height(8.dp))
                        Text("Vui lòng kiểm tra mạng hoặc thanh toán bằng tiền mặt.", color = NeutralSecondary, textAlign = TextAlign.Center)
                    } else {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .border(1.dp, NeutralBorder, RoundedCornerShape(20.dp))
                                .padding(18.dp)
                        ) {
                            QrCodeView(paymentQrData, Modifier.size(270.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Quét mã bằng ứng dụng ngân hàng", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sau khi thanh toán thành công, hệ thống sẽ tự chuyển sang bước chụp.",
                            style = MaterialTheme.typography.body2,
                            color = NeutralSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text("Chưa bật thanh toán QR", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText)
                    Spacer(Modifier.height(8.dp))
                    Text("Nhân viên có thể xác nhận tiền mặt để tiếp tục phiên chụp.", color = NeutralSecondary, textAlign = TextAlign.Center)
                }
            }
        }

        StaffCashAuthorizationButton(
            onClick = { showStaffPinDialog = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
        )

        if (showStaffPinDialog) {
            StaffCashAuthorizationDialog(
                totalAmount = totalAmount,
                expectedPin = staffCashPin,
                onDismiss = { showStaffPinDialog = false },
                onAuthorized = {
                    showStaffPinDialog = false
                    onPaid()
                }
            )
        }
    }
}

@Composable
fun StaffCashAuthorizationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassButtonSurface(
        onClick = onClick,
        palette = SecondaryGlassPalette,
        cornerRadius = LiquidGlassMetrics.floatingDockCornerRadius,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = AccentNudeDark,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Nhân viên xác nhận tiền mặt",
            color = NeutralSecondary,
            style = MaterialTheme.typography.button,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StaffCashAuthorizationDialog(
    totalAmount: Long,
    expectedPin: String,
    onDismiss: () -> Unit,
    onAuthorized: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun triggerValidation() {
        if (enteredPin.length < 4) return
        if (enteredPin == expectedPin) {
            onAuthorized()
        } else {
            hasError = true
            enteredPin = ""
            scope.launch {
                val offsets = listOf(-14f, 14f, -10f, 10f, -6f, 6f, 0f)
                for (target in offsets) {
                    shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 45))
                }
            }
        }
    }

    fun appendDigit(digit: Char) {
        if (enteredPin.length < 4) {
            hasError = false
            enteredPin += digit
        }
    }

    fun removeLastDigit() {
        if (enteredPin.isNotEmpty()) {
            hasError = false
            enteredPin = enteredPin.dropLast(1)
        }
    }

    fun clearDigits() {
        hasError = false
        enteredPin = ""
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val code = event.key.nativeKeyCode
                        when {
                            code in java.awt.event.KeyEvent.VK_0..java.awt.event.KeyEvent.VK_9 -> {
                                appendDigit(('0'.code + (code - java.awt.event.KeyEvent.VK_0)).toChar())
                                true
                            }
                            code in java.awt.event.KeyEvent.VK_NUMPAD0..java.awt.event.KeyEvent.VK_NUMPAD9 -> {
                                appendDigit(('0'.code + (code - java.awt.event.KeyEvent.VK_NUMPAD0)).toChar())
                                true
                            }
                            event.key == Key.Backspace || code == java.awt.event.KeyEvent.VK_BACK_SPACE -> {
                                removeLastDigit()
                                true
                            }
                            event.key == Key.Delete || code == java.awt.event.KeyEvent.VK_DELETE -> {
                                clearDigits()
                                true
                            }
                            event.key == Key.Enter || event.key == Key.NumPadEnter || code == java.awt.event.KeyEvent.VK_ENTER -> {
                                if (enteredPin.length == 4) triggerValidation()
                                true
                            }
                            event.key == Key.Escape || code == java.awt.event.KeyEvent.VK_ESCAPE -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .liquidGlassPlate(
                    variant = LiquidGlassPlateVariant.Elevated,
                    cornerRadius = LiquidGlassPlateMetrics.majorCornerRadius,
                    elevation = LiquidGlassPlateMetrics.elevatedElevation
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentNudeLight)
                    .border(1.dp, AccentNude.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = AccentNudeDark,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Xác nhận thu tiền mặt",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Black,
                color = NeutralText
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Dành riêng cho nhân viên Photobooth",
                style = MaterialTheme.typography.body2,
                color = NeutralSecondary
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassPlate(
                        variant = LiquidGlassPlateVariant.Standard,
                        cornerRadius = LiquidGlassPlateMetrics.rowCornerRadius,
                        elevation = LiquidGlassPlateMetrics.nestedRowElevation
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Số tiền thu:",
                    style = MaterialTheme.typography.body2,
                    color = NeutralSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatVnd(totalAmount),
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Black,
                    color = AccentNudeDark
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.graphicsLayer { translationX = shakeOffset.value },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isFilled = index < enteredPin.length
                    val isCurrent = index == enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    hasError -> AccentRed.copy(alpha = 0.08f)
                                    isFilled -> AccentNudeLight
                                    else -> NeutralPanel
                                }
                            )
                            .border(
                                width = if (isCurrent || isFilled || hasError) 2.dp else 1.dp,
                                color = when {
                                    hasError -> AccentRed
                                    isFilled || isCurrent -> AccentNude
                                    else -> NeutralBorder
                                },
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFilled) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(AccentNudeDark)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .height(28.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                this@Column.AnimatedVisibility(
                    visible = hasError,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = "Mã PIN không chính xác",
                        color = AccentRed,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Xóa", "0", "BACKSPACE")
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rows.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowKeys.forEach { key ->
                            val isUtility = key == "Xóa" || key == "BACKSPACE"
                            LiquidGlassButtonSurface(
                                onClick = {
                                    when (key) {
                                        "Xóa" -> clearDigits()
                                        "BACKSPACE" -> removeLastDigit()
                                        else -> appendDigit(key.first())
                                    }
                                },
                                palette = if (isUtility) NeutralGlassPalette else SecondaryGlassPalette,
                                cornerRadius = 14.dp,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                            ) {
                                if (key == "BACKSPACE") {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Xóa lùi",
                                        tint = NeutralSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        style = if (isUtility) MaterialTheme.typography.subtitle2 else MaterialTheme.typography.h5,
                                        fontWeight = if (isUtility) FontWeight.Bold else FontWeight.Black,
                                        color = if (isUtility) NeutralSecondary else NeutralText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KioskSecondaryButton(
                    text = "Hủy",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                KioskPrimaryButton(
                    text = "Xác nhận",
                    onClick = { triggerValidation() },
                    enabled = enteredPin.length == 4,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
