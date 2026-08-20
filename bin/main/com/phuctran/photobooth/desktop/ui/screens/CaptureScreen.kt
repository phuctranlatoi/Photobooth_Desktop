package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.domain.SessionState
import com.phuctran.photobooth.desktop.model.*
import com.phuctran.photobooth.desktop.ui.components.*
import com.phuctran.photobooth.desktop.ui.theme.*
import java.nio.file.Path

@Composable
fun CaptureScreen(
    state: SessionState,
    layout: LayoutMode,
    effect: EffectMode,
    countdown: Int,
    capturedMoments: List<CapturedMoment>,
    captureSources: List<Path>,
    cameraDevices: List<String>,
    liveViewBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    isRecordingVideo: Boolean = false,
    onImportSources: () -> Unit,
    onClearSources: () -> Unit,
    onRefreshCameraDevices: () -> Unit,
    onStartCapture: () -> Unit
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PanelBox(Modifier.width(320.dp).fillMaxHeight()) {
            SectionHeader(
                badge = "Bước 4",
                title = "Chụp ảnh",
                subtitle = "Chuẩn bị tạo dáng thật đẹp nhé!"
            )
            Spacer(Modifier.height(16.dp))
            
            Text("Trạng thái thiết bị", fontWeight = FontWeight.Bold, color = NeutralText)
            Spacer(Modifier.height(8.dp))
            if (cameraDevices.isNotEmpty()) {
                Text("Camera: ${cameraDevices.first()}", color = NeutralMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Không tìm thấy camera", color = AccentNude)
                BouncyButton(onClick = onRefreshCameraDevices, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Làm mới")
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Text("Tiến trình chụp", fontWeight = FontWeight.Bold, color = NeutralText)
            Text("${capturedMoments.size} / ${layout.shotCount} ảnh", color = AccentNude, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black)
            
            Spacer(Modifier.weight(1f))
            if (state == SessionState.LIVE_VIEW || state == SessionState.IDLE || state == SessionState.SELECTING_QUANTITY || state == SessionState.PREPARING) {
                BouncyButton(
                    onClick = onStartCapture,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentNude, contentColor = Color.White)
                ) {
                    Text("BẮT ĐẦU CHỤP", fontWeight = FontWeight.Black, style = MaterialTheme.typography.h6)
                }
            }
        }
        
        PanelBox(Modifier.weight(1f).fillMaxHeight()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Video Preview
                if (cameraDevices.isEmpty()) {
                    Text("Camera chưa sẵn sàng", color = NeutralMuted)
                } else if (liveViewBitmap != null && (state == SessionState.LIVE_VIEW || state == SessionState.COUNTDOWN || state == SessionState.PREPARING || state == SessionState.CAPTURING)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = liveViewBitmap,
                            contentDescription = "Live View",
                            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = -1f), // Lật ngược như soi gương
                            contentScale = ContentScale.Fit
                        )
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val boxWidth = size.width
                            val boxHeight = size.height
                            val boxAspect = boxWidth / boxHeight
                            val imageAspect = liveViewBitmap.width.toFloat() / liveViewBitmap.height.toFloat()
                            
                            var drawWidth = boxWidth
                            var drawHeight = boxHeight
                            
                            if (imageAspect > boxAspect) {
                                drawHeight = boxWidth / imageAspect
                            } else {
                                drawWidth = boxHeight * imageAspect
                            }
                            
                            val xOffset = (boxWidth - drawWidth) / 2f
                            val yOffset = (boxHeight - drawHeight) / 2f
                            
                            val targetAspect = layout.photoAspectRatio
                            var cropWidth = drawWidth
                            var cropHeight = drawHeight
                            
                            if (imageAspect > targetAspect) {
                                cropHeight = drawHeight
                                cropWidth = drawHeight * targetAspect
                            } else {
                                cropWidth = drawWidth
                                cropHeight = drawWidth / targetAspect
                            }
                            
                            val cropLeft = xOffset + (drawWidth - cropWidth) / 2f
                            val cropTop = yOffset + (drawHeight - cropHeight) / 2f
                            
                            val dimColor = Color.Black.copy(alpha = 0.75f)
                            
                            // Top
                            if (cropTop > yOffset) {
                                drawRect(dimColor, topLeft = androidx.compose.ui.geometry.Offset(xOffset, yOffset), size = androidx.compose.ui.geometry.Size(drawWidth, cropTop - yOffset))
                            }
                            // Bottom
                            if (yOffset + drawHeight > cropTop + cropHeight) {
                                drawRect(dimColor, topLeft = androidx.compose.ui.geometry.Offset(xOffset, cropTop + cropHeight), size = androidx.compose.ui.geometry.Size(drawWidth, (yOffset + drawHeight) - (cropTop + cropHeight)))
                            }
                            // Left
                            if (cropLeft > xOffset) {
                                drawRect(dimColor, topLeft = androidx.compose.ui.geometry.Offset(xOffset, cropTop), size = androidx.compose.ui.geometry.Size(cropLeft - xOffset, cropHeight))
                            }
                            // Right
                            if (xOffset + drawWidth > cropLeft + cropWidth) {
                                drawRect(dimColor, topLeft = androidx.compose.ui.geometry.Offset(cropLeft + cropWidth, cropTop), size = androidx.compose.ui.geometry.Size((xOffset + drawWidth) - (cropLeft + cropWidth), cropHeight))
                            }
                            
                            drawRect(
                                color = Color.White.copy(alpha = 0.8f),
                                topLeft = androidx.compose.ui.geometry.Offset(cropLeft, cropTop),
                                size = androidx.compose.ui.geometry.Size(cropWidth, cropHeight),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        }
                        // Overlay Loading khi đang chờ ảnh
                        if (state == com.phuctran.photobooth.desktop.domain.SessionState.CAPTURING) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFFD4AF37))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Đang lưu ảnh từ máy cơ...", color = Color.White, style = MaterialTheme.typography.h6)
                                }
                            }
                        }
                    }
                } else {
                    // Fallback UI when Live View is not available (e.g., EOS Utility)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (state) {
                                SessionState.LIVE_VIEW, SessionState.PREPARING -> {
                                    Text("EOS Utility đang sẵn sàng", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.h6)
                                }
                                SessionState.COUNTDOWN -> {
                                    Text("Hãy chuẩn bị tạo dáng!", color = Color.White, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
                                }
                                SessionState.CAPTURING -> {
                                    Text("Đang lưu ảnh...", color = AccentNude, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    CircularProgressIndicator(color = AccentNude)
                                }
                                else -> {}
                            }
                        }
                    }
                }
                
                // Countdown Overlay
                if (countdown > 0) {
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (countdown > 0) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 400f)
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(240.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                                .clip(CircleShape)
                                .background(AccentNude.copy(alpha = 0.85f)), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(countdown.toString(), color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(120f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Black)
                        }
                    }
                }
                
                // Animated Flash Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = state == SessionState.CAPTURING,
                    enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(50)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(500))
                ) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                }
            }
        }
    }
}
