package cl.efficientchile.inventario.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cl.efficientchile.inventario.data.Especimen
import cl.efficientchile.inventario.data.Repo
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerScreen(
    repo: Repo,
    baseUrl: String,
    token: String,
    uidsYaEnCarrito: Set<String>,
    onEscaneado: (Especimen) -> Unit,
    onCerrar: () -> Unit,
) {
    val ctx = LocalContext.current
    val life = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var permisoOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permisoOk = it }

    LaunchedEffect(Unit) {
        if (!permisoOk) permLauncher.launch(Manifest.permission.CAMERA)
    }

    // Estado de escaneo (evita disparar dos veces el mismo QR)
    var buscando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var mensajeError by remember { mutableStateOf(false) }
    val vistoRecientemente = remember { mutableSetOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanear QR") },
                navigationIcon = {
                    TextButton(onClick = onCerrar) { Text("Cerrar") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (!permisoOk) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Se necesita permiso de cámara para escanear.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Dar permiso")
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val previewView = PreviewView(context)
                        val providerFuture = ProcessCameraProvider.getInstance(context)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val opts = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                            val scanner = BarcodeScanning.getClient(opts)
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            val executor = Executors.newSingleThreadExecutor()
                            analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                                val media = proxy.image
                                if (media == null || buscando) {
                                    proxy.close(); return@setAnalyzer
                                }
                                val img = InputImage.fromMediaImage(
                                    media, proxy.imageInfo.rotationDegrees
                                )
                                scanner.process(img)
                                    .addOnSuccessListener { codes ->
                                        val uid = codes.firstOrNull()?.rawValue
                                        if (!uid.isNullOrBlank() && uid !in vistoRecientemente) {
                                            vistoRecientemente.add(uid)
                                            if (uid in uidsYaEnCarrito) {
                                                mensaje = "Ese QR ya está en el carrito."
                                                mensajeError = true
                                                return@addOnSuccessListener
                                            }
                                            buscando = true
                                            mensaje = "Consultando servidor…"
                                            mensajeError = false
                                            scope.launch {
                                                try {
                                                    val esp = repo.leerEspecimen(baseUrl, token, uid)
                                                    onEscaneado(esp)
                                                } catch (e: Exception) {
                                                    mensaje = e.message ?: "Error al consultar"
                                                    mensajeError = true
                                                    buscando = false
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                life, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                            )
                        }, ContextCompat.getMainExecutor(context))
                        previewView
                    },
                )

                // Overlay mensaje
                mensaje?.let { m ->
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                            .background(
                                if (mensajeError) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                            .padding(16.dp),
                    ) {
                        Text(
                            m,
                            color = if (mensajeError)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Marco guía en el centro
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(240.dp)
                        .background(Color.Transparent),
                )
            }
        }
    }
}
