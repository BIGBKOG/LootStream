package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScannerDialog(
    onQrScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val handledOnce = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { analysisExecutor.shutdown() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("qr_scanner_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonCyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = NeonCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.hostQrScannerTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = s.hostQrScannerSubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("qr_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = s.cancelAction)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission && cameraError == null) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            },
                            update = { previewView ->
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                cameraProviderFuture.addListener(
                                    {
                                        runCatching {
                                            val provider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.surfaceProvider = previewView.surfaceProvider
                                            }
                                            val analysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                                .also { imgAnalysis ->
                                                    val reader = QRCodeReader()
                                                    val hints = mapOf(
                                                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                                        DecodeHintType.TRY_HARDER to true
                                                    )
                                                    imgAnalysis.setAnalyzer(analysisExecutor) { image ->
                                                        decodeQrFromImageProxy(image, reader, hints)?.let { decoded ->
                                                            if (handledOnce.compareAndSet(false, true)) {
                                                                previewView.post {
                                                                    onQrScanned(decoded)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                            provider.unbindAll()
                                            val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                                CameraSelector.DEFAULT_BACK_CAMERA
                                            } else {
                                                CameraSelector.DEFAULT_FRONT_CAMERA
                                            }
                                            val cam = provider.bindToLifecycle(
                                                lifecycleOwner,
                                                selector,
                                                preview,
                                                analysis
                                            )
                                            boundCamera = cam
                                            cam.cameraControl.enableTorch(torchEnabled)
                                        }.onFailure { err ->
                                            cameraError = err.message ?: "Camera unavailable"
                                        }
                                    },
                                    ContextCompat.getMainExecutor(context)
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        QrReticleOverlay(
                            accent = NeonGreen,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                            IconButton(
                                onClick = {
                                    val next = !torchEnabled
                                    torchEnabled = next
                                    boundCamera?.cameraControl?.enableTorch(next)
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                                    .testTag("qr_torch_btn")
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Toggle flashlight",
                                    tint = if (torchEnabled) NeonCyan else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = cameraError ?: s.hostQrCameraPermissionHint,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (cameraError == null) {
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("qr_grant_camera_btn")
                                ) {
                                    Text(
                                        text = s.hostQrGrantCameraBtn,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrReticleOverlay(
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val boxSize = size.minDimension * 0.64f
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize
        val corner = boxSize * 0.16f
        val stroke = 4.dp.toPx()

        // Subtle dimmed surround
        drawRect(color = Color.Black.copy(alpha = 0.25f))

        // Top-left corner
        drawLine(accent, Offset(left, top), Offset(left + corner, top), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(accent, Offset(left, top), Offset(left, top + corner), strokeWidth = stroke, cap = StrokeCap.Round)
        // Top-right corner
        drawLine(accent, Offset(right - corner, top), Offset(right, top), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(accent, Offset(right, top), Offset(right, top + corner), strokeWidth = stroke, cap = StrokeCap.Round)
        // Bottom-left corner
        drawLine(accent, Offset(left, bottom - corner), Offset(left, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(accent, Offset(left, bottom), Offset(left + corner, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
        // Bottom-right corner
        drawLine(accent, Offset(right - corner, bottom), Offset(right, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(accent, Offset(right, bottom - corner), Offset(right, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private fun decodeQrFromImageProxy(
    image: ImageProxy,
    reader: QRCodeReader,
    hints: Map<DecodeHintType, Any>
): String? {
    return try {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val yBytes = ByteArray(width * height)
        if (pixelStride == 1 && rowStride == width) {
            buffer.rewind()
            buffer.get(yBytes)
        } else {
            val rowBuf = ByteArray(rowStride)
            for (r in 0 until height) {
                buffer.position(r * rowStride)
                val toRead = minOf(rowStride, buffer.remaining())
                buffer.get(rowBuf, 0, toRead)
                var col = 0
                while (col < width) {
                    yBytes[r * width + col] = rowBuf[col * pixelStride]
                    col++
                }
            }
        }

        val source = PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        reader.decode(bitmap, hints)?.text
    } catch (_: Throwable) {
        null
    } finally {
        reader.reset()
        image.close()
    }
}
