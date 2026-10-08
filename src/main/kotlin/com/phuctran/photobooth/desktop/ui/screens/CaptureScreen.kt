package com.phuctran.photobooth.desktop.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CaptureScreen(
    state: SessionState,
    layout: LayoutMode,
    effect: EffectMode,
    countdown: Int,
    capturedMoments: List<CapturedMoment>,
    captureSources: List<Path>,
    cameraDevices: List<String>,
    liveViewStream: StateFlow<ImageBitmap?>,
    isRecordingVideo: Boolean = false,
    onImportSources: () -> Unit,
    onClearSources: () -> Unit,
    onRefreshCameraDevices: () -> Unit,
    onStartCapture: () -> Unit
) {
    val shutterInteractionSource = remember { MutableInteractionSource() }
    val shutterPressed by shutterInteractionSource.collectIsPressedAsState()
    val shutterScale by animateFloatAsState(
        targetValue = if (shutterPressed) 0.88f else 1f,
        animationSpec = if (shutterPressed) tween(80) else spring(),
        label = "Shutter press"
    )
    Box(Modifier.fillMaxSize().background(CameraBlack)) {
        CaptureLiveView(state, layout, cameraDevices, liveViewStream)

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.42f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Ảnh ${capturedMoments.size}/${layout.shotCount}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.subtitle1
            )
            if (isRecordingVideo) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(AccentRed))
                    Text("Đang quay video", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state == SessionState.LIVE_VIEW || state == SessionState.PREPARING) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .graphicsLayer { scaleX = shutterScale; scaleY = shutterScale }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable(
                            interactionSource = shutterInteractionSource,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Chụp ảnh",
                            onClick = onStartCapture
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, AccentNude.copy(alpha = 0.5f), CircleShape)
                    )
                }
                Text("Chạm để chụp", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
            }
        }

        if (countdown > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                androidx.compose.animation.Crossfade(
                    targetState = countdown,
                    animationSpec = androidx.compose.animation.core.tween(200)
                ) { count ->
                    Text(
                        text = count.toString(),
                        color = Color.White,
                        fontSize = androidx.compose.ui.unit.TextUnit(240f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = AccentNude.copy(alpha = 0.8f),
                                blurRadius = 30f
                            )
                        )
                    )
                }
            }
        }
        
        // Animated Flash Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = state == SessionState.CAPTURING,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(50)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(500)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().background(Color.White))
        }
    }
}

@Composable
private fun CaptureLiveView(
    state: SessionState,
    layout: LayoutMode,
    cameraDevices: List<String>,
    liveViewStream: StateFlow<ImageBitmap?>
) {
    val liveViewBitmap by liveViewStream.collectAsState()
    val currentBitmap = liveViewBitmap
    Box(Modifier.fillMaxSize()) {
        if (cameraDevices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Camera chưa sẵn sàng", color = Color.White, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black)
                    Text("Vui lòng kiểm tra nguồn camera hoặc gọi nhân viên.", color = Color.White.copy(alpha = 0.64f), style = MaterialTheme.typography.body1)
                }
            }
        } else if (currentBitmap != null && (state == SessionState.LIVE_VIEW || state == SessionState.COUNTDOWN || state == SessionState.PREPARING || state == SessionState.CAPTURING)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Image(
                    bitmap = currentBitmap,
                    contentDescription = "Live View",
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = -1f), // Lật ngược như soi gương
                    contentScale = ContentScale.Fit
                )
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxWidth = size.width
                    val boxHeight = size.height
                    val boxAspect = boxWidth / boxHeight
                    val imageAspect = currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
                    
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
                    
                    val dimColor = Color.Black.copy(alpha = 0.68f)
                    
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
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = androidx.compose.ui.geometry.Offset(cropLeft, cropTop),
                        size = androidx.compose.ui.geometry.Size(cropWidth, cropHeight),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }
            }
        } else {
            // Fallback UI
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state == SessionState.CAPTURING) {
                        Text("Đang lưu ảnh...", color = Color.White, style = MaterialTheme.typography.h5)
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Text("Hãy chuẩn bị tạo dáng!", color = Color.White, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

    }
}
