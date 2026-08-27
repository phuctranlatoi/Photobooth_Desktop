package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phuctran.photobooth.desktop.ui.theme.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.nio.file.Path
import java.nio.file.Files
import com.phuctran.photobooth.desktop.model.CapturedMoment
import com.phuctran.photobooth.desktop.model.LayoutMode
import com.phuctran.photobooth.desktop.model.EffectMode
import com.phuctran.photobooth.desktop.model.FramePack
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy



// Modifier extension for bouncy click
fun Modifier.bouncyClickable(
    interactionSource: MutableInteractionSource = MutableInteractionSource(),
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick
)

@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(backgroundColor = InkSoft, contentColor = Color.White),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}

@Composable
fun KioskPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BouncyButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (enabled) AccentNude else NeutralBorder,
            contentColor = if (enabled) Color.White else NeutralMuted
        ),
        modifier = modifier.heightIn(min = 56.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.button, maxLines = 1)
    }
}

@Composable
fun KioskSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = NeutralPanel,
            contentColor = NeutralText,
            disabledContentColor = NeutralMuted
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorder),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 56.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.button, maxLines = 1)
    }
}

@Composable
fun KioskBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    light: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (light) Color.White.copy(alpha = 0.22f) else NeutralPanel)
            .border(1.dp, if (light) Color.White.copy(alpha = 0.35f) else NeutralBorder, CircleShape)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Quay lại",
            tint = if (light) Color.White else NeutralText
        )
    }
}

@Composable
fun PanelBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .border(1.dp, NeutralBorder.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = NeutralPanel,
        contentColor = NeutralText,
        elevation = 0.dp
    ) {
        Column(Modifier.padding(24.dp)) {
            content()
        }
    }
}

@Composable
fun SectionHeader(badge: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (badge.isNotBlank()) {
                Box(Modifier.background(AccentNudeLight, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(badge, color = AccentNudeDark, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Black)
                }
            }
            Text(title, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = NeutralText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = NeutralSecondary, style = MaterialTheme.typography.body2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, bgColor: Color = NeutralBg, textColor: Color = NeutralText) {
    Text(
        text,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .border(1.dp, NeutralBorder.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = textColor,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.caption
    )
}

@Composable
fun StatusChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.11f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = NeutralSecondary, style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold)
        Text(value, color = color, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Black, maxLines = 1)
    }
}


fun formatVnd(amount: Long): String = String.format("%,d đ", amount)

fun loadImageBitmap(path: Path): ImageBitmap? {
    return try {
        org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(path)).toComposeImageBitmap()
    } catch (e: Exception) { null }
}

fun loadFrameThumbnail(path: Path): ImageBitmap? {
    try {
        if (!Files.exists(path)) return null
        val thumbPath = path.resolveSibling("thumb_" + path.fileName.toString())
        
        if (Files.exists(thumbPath)) {
            return org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(thumbPath)).toComposeImageBitmap()
        }

        Files.newInputStream(path).use { stream ->
            javax.imageio.ImageIO.createImageInputStream(stream).use { iis ->
                val readers = javax.imageio.ImageIO.getImageReaders(iis)
                if (readers.hasNext()) {
                    val reader = readers.next()
                    reader.setInput(iis, true, true)
                    val param = reader.defaultReadParam
                    param.setSourceSubsampling(4, 4, 0, 0)
                    val img = reader.read(0, param)
                    reader.dispose()
                    
                    if (img != null) {
                        try {
                            Files.newOutputStream(thumbPath).use { out ->
                                javax.imageio.ImageIO.write(img, "png", out)
                            }
                        } catch(e: Exception) { e.printStackTrace() }
                        return img.toComposeImageBitmap()
                    }
                }
            }
        }
        return null
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

@Composable
fun MomentTile(moment: CapturedMoment, selectedOrder: Int?, aspectRatio: Float, isDimmed: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap = remember(moment.photoPath) { moment.photoPath?.let(::loadImageBitmap) }
    
    val scale by androidx.compose.animation.core.animateFloatAsState(if (selectedOrder != null) 0.95f else 1f)
    val dimAlpha by androidx.compose.animation.core.animateFloatAsState(if (isDimmed) 0.4f else 0f)

    Box(
        modifier
            .aspectRatio(aspectRatio)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(NeutralPanelAlt)
            .border(if (selectedOrder != null) 4.dp else 1.dp, if (selectedOrder != null) AccentNude else NeutralBorder, RoundedCornerShape(14.dp))
            .bouncyClickable(onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (bitmap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(moment.photoLabel, color = NeutralMuted, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
            }
        }
        
        // Dim overlay
        if (dimAlpha > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
        }

        // Selection Badge
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedOrder != null,
            enter = androidx.compose.animation.scaleIn(animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f)),
            exit = androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AccentNude)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("${selectedOrder ?: ""}", color = Color.White, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun ChoiceRow(title: String, subtitle: String, selected: Boolean, accent: Color, onClick: () -> Unit, trailing: String? = null) {
    val scale by animateFloatAsState(if (selected) 1.01f else 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else NeutralPanel)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else NeutralBorder, RoundedCornerShape(14.dp))
            .bouncyClickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = NeutralText, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = NeutralSecondary, style = MaterialTheme.typography.body2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Color.White else NeutralPanelAlt)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(trailing, color = if (selected) accent else NeutralSecondary, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun LayoutChoice(layout: LayoutMode, selected: Boolean, onClick: () -> Unit) {
    ChoiceRow(
        title = layout.title,
        subtitle = layout.description,
        selected = selected,
        accent = AccentNude,
        onClick = onClick,
        trailing = "${layout.selectCount} ảnh"
    )
}

@Composable
fun FrameChoice(frame: FramePack, selected: Boolean, onClick: () -> Unit) {
    if (frame.customImagePath != null) {
        val imageBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, frame.customImagePath) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                loadFrameThumbnail(frame.customImagePath!!)
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NeutralPanel)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) Color(frame.accentColor) else NeutralBorder,
                    RoundedCornerShape(14.dp)
                )
                .bouncyClickable(onClick = onClick)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = imageBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = frame.title,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(NeutralPanelAlt))
                    Text("Đang tải khung", color = NeutralMuted, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(frame.accentColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OK", color = Color.White, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Black)
                }
            }
        }
    } else {
        ChoiceRow(
            title = frame.title,
            subtitle = frame.description,
            selected = selected,
            accent = AccentNude,
            onClick = onClick,
            trailing = if (frame.isCustom) "Custom" else null
        )
    }
}

@Composable
fun OutputRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeutralPanelAlt.copy(alpha = 0.75f))
            .border(1.dp, NeutralBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NeutralSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.body2)
        Text(value, color = NeutralText, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun QuantityCard(quantity: Int, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.04f else 1f)
    Box(
        Modifier
            .size(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) AccentNudeLight else NeutralPanel)
            .border(if (selected) 3.dp else 1.dp, if (selected) AccentNude else NeutralBorder, RoundedCornerShape(16.dp))
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$quantity", color = NeutralText, style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black)
            Text("bản in", color = if (selected) AccentNudeDark else NeutralSecondary, style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QrMock(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, NeutralBorder, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Text("QR CODE", color = NeutralMuted, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CameraPoseGuide(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(CameraBlack).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(28.dp)) {
            val stroke = Stroke(2.dp.toPx())
            val guide = Color.White.copy(alpha = 0.18f)
            drawRoundRect(guide, cornerRadius = CornerRadius(24.dp.toPx()), style = stroke)
            drawLine(guide, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = 1.dp.toPx())
            drawLine(guide, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = 1.dp.toPx())
            drawLine(guide, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = 1.dp.toPx())
            drawLine(guide, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = 1.dp.toPx())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Căn giữa khung hình", color = Color.White, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black)
            Text("Nhìn vào camera và giữ dáng khi đếm ngược", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.body2)
        }
    }
}

@Composable
fun CameraDeviceRow(index: Int, name: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(NeutralBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$index", color = NeutralMuted, style = MaterialTheme.typography.caption, modifier = Modifier.width(24.dp))
        Text(name, color = NeutralText, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SourceRow(index: Int, path: Path) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(NeutralBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$index", color = NeutralMuted, style = MaterialTheme.typography.caption, modifier = Modifier.width(24.dp))
        Text(path?.fileName?.toString() ?: "Unknown", color = NeutralText, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CaptureMask(photoAspectRatio: Float) {
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(Color(0xAA000000))
        val containerRatio = size.width / size.height
        val targetWidth: Float
        val targetHeight: Float
        if (containerRatio > photoAspectRatio) {
            targetHeight = size.height * 0.92f
            targetWidth = targetHeight * photoAspectRatio
        } else {
            targetWidth = size.width * 0.92f
            targetHeight = targetWidth / photoAspectRatio
        }
        val left = (size.width - targetWidth) / 2f
        val top = (size.height - targetHeight) / 2f
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(left, top),
            size = Size(targetWidth, targetHeight),
            cornerRadius = CornerRadius(16.dp.toPx()),
            blendMode = BlendMode.Clear
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(left, top),
            size = Size(targetWidth, targetHeight),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(4.dp.toPx())
        )
    }
}

@Composable
fun CameraEffectOverlay(effect: EffectMode) {
    Box(Modifier.fillMaxSize().background(Color(0xFFE5E5E5).copy(alpha = 0.2f)))
}

@Composable
fun CountdownBubble(countdown: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$countdown", color = Color.White, style = MaterialTheme.typography.h1, fontWeight = FontWeight.Black)
    }
}

@Composable
fun FlashEffect(isCapturing: Boolean) {
    val alpha by animateFloatAsState(if (isCapturing) 1f else 0f, tween(100))
    if (alpha > 0f) {
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = alpha)))
    }
}

@Composable
fun RealQr(url: String, modifier: Modifier = Modifier) {
    val qrBitmap = remember(url) {
        try {
            val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                url, 
                com.google.zxing.BarcodeFormat.QR_CODE, 
                512, 
                512,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    image.setRGB(x, y, if (bitMatrix.get(x, y)) java.awt.Color.BLACK.rgb else java.awt.Color.WHITE.rgb)
                }
            }
            image.toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, NeutralBorder, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap,
                contentDescription = "QR Code",
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("Lỗi QR", color = NeutralMuted, fontWeight = FontWeight.Black)
        }
    }
}

fun String.toComposeColor(): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(java.awt.Color.decode(if (this.startsWith("#")) this else "#${this}").rgb)
