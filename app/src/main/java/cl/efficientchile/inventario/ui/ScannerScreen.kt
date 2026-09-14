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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.efficientchile.inventario.data.Especimen
import cl.efficientchile.inventario.data.Repo
import cl.efficientchile.inventario.util.Campanita
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Lector de QR.
 *
 * Dos decisiones que importan en el meson:
 *
 * 1. Un QR ya leido no vuelve a contarse. La camara dispara varias veces por
 *    segundo sobre el mismo codigo; sin memoria, un solo escaneo agregaria
 *    la misma planta cinco veces y el inventario quedaria descuadrado.
 * 2. Despues de leer, la pantalla NO se cierra sola hasta que el servidor
 *    conteste. Si se cerrara al instante, el vendedor creeria que quedo
 *    agregado cuando en realidad el QR podia estar vendido o no existir.
 */
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
    val dueno = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var permiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permiso = it }

    LaunchedEffect(Unit) {
        if (!permiso) pedirPermiso.launch(Manifest.permission.CAMERA)
    }

    var consultando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    // Lo ya visto en ESTA sesion de camara, para no leer dos veces lo mismo.
    val vistos = remember { mutableStateListOf<String>() }

    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { ejecutor.shutdown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escanear QR") },
                actions = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!permiso) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("La app necesita la cámara para leer los QR.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { pedirPermiso.launch(Manifest.permission.CAMERA) }) {
                        Text("Dar permiso")
                    }
                }
                return@Column
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        val vista = PreviewView(c)
                        val futuro = ProcessCameraProvider.getInstance(c)
                        futuro.addListener({
                            val proveedor = futuro.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(vista.surfaceProvider)
                            }
                            val lector = BarcodeScanning.getClient()
                            val analisis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analisis.setAnalyzer(ejecutor) { imagen ->
                                procesar(imagen, lector) { texto ->
                                    if (texto in vistos || texto in uidsYaEnCarrito) return@procesar
                                    if (consultando) return@procesar
                                    vistos.add(texto)
                                    consultando = true
                                    mensaje = "Consultando…"
                                    scope.launch {
                                        try {
                                            val esp = repo.leerEspecimen(baseUrl, token, texto)
                                            if (!esp.disponible) {
                                                mensaje = "${esp.nombre}: ya está vendido."
                                            } else {
                                                Campanita.sonar()
                                                onEscaneado(esp)
                                            }
                                        } catch (e: Exception) {
                                            // Se saca de vistos: si fue un corte
                                            // de red, el vendedor tiene que poder
                                            // reintentar con el mismo QR.
                                            vistos.remove(texto)
                                            mensaje = e.message ?: "No se pudo leer el QR"
                                        } finally {
                                            consultando = false
                                        }
                                    }
                                }
                            }
                            proveedor.unbindAll()
                            proveedor.bindToLifecycle(
                                dueno, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis)
                        }, ContextCompat.getMainExecutor(c))
                        vista
                    },
                )
                if (consultando) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text(
                    mensaje ?: "Apunta al QR de la planta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mensaje != null && !consultando)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uidsYaEnCarrito.isNotEmpty()) {
                    Text(
                        "${uidsYaEnCarrito.size} en el carrito",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Saca el texto del primer QR del cuadro, si hay alguno. */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun procesar(
    imagen: ImageProxy,
    lector: com.google.mlkit.vision.barcode.BarcodeScanner,
    onTexto: (String) -> Unit,
) {
    val media = imagen.image
    if (media == null) { imagen.close(); return }
    val entrada = InputImage.fromMediaImage(media, imagen.imageInfo.rotationDegrees)
    lector.process(entrada)
        .addOnSuccessListener { codigos ->
            codigos.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                ?.rawValue?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(onTexto)
        }
        .addOnCompleteListener { imagen.close() }
}
