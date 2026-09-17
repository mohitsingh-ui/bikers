package com.ridesync.app.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.ridesync.app.core.RLog
import com.ridesync.app.networking.protocol.RideCodes
import com.ridesync.app.ui.components.BorderedInfo
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.Space
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ridesync.app.ui.theme.RideSyncTheme
import java.util.concurrent.Executors

/**
 * Camera QR scanner using CameraX + zxing. Decodes a ridesync://join deep link
 * and hands the parsed target back. Camera permission is requested inline and
 * only here — never up front.
 */
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onScanned: (RideCodes.JoinTarget) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    RideScaffold(title = "Scan Ride QR", onBack = onBack) { mod ->
        Box(mod.fillMaxSize()) {
            if (hasPermission) {
                CameraPreview(onScanned = onScanned)
                // Framing hint overlay
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(Space.l),
                ) {
                    Text(
                        "Point at the host’s QR code",
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.xl))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(Space.l), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    BorderedInfo(
                        icon = Icons.Filled.CameraAlt,
                        tint = RideSyncTheme.colors.accent,
                        title = "Camera permission needed",
                        body = "RideSync uses the camera only to scan a ride’s QR code. Nothing is recorded.",
                        actionText = "Allow camera",
                        onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (RideCodes.JoinTarget) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var handled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, QrAnalyzer { text ->
                    if (handled) return@QrAnalyzer
                    val target = RideCodes.parseJoinUri(text)
                    if (target != null) {
                        handled = true
                        onScanned(target)
                    }
                })
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    RLog.w(RLog.Cat.UI, "camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/** zxing analyzer over CameraX YUV frames. */
private class QrAnalyzer(private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    override fun analyze(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val source = PlanarYUVLuminanceSource(
                data, image.width, image.height, 0, 0, image.width, image.height, false,
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(bitmap, hints)
            onQr(result.text)
        } catch (_: Exception) {
            // No QR in frame — normal, ignore.
        } finally {
            image.close()
        }
    }
}
