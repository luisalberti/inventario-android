package cl.efficientchile.inventario.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cl.efficientchile.inventario.util.Fotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foto del comprobante de transferencia desde la pantalla del cliente.
 *
 * Dos etapas: primero la camara, despues la revision. La revision es el
 * punto de la pantalla — el trabajador tiene que poder leer monto, fecha y
 * destinatario antes de aceptar, y para eso la vista previa permite hacer
 * zoom con dos dedos. Solo cuando aprueba se devuelve el archivo.
 */
@Composable
fun ComprobanteScreen(
    onListo: (File) -> Unit,
    onCancelar: () -> Unit,
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
        ActivityResultContracts.RequestPermission()
    ) { permisoOk = it }

    LaunchedEffect(Unit) { if (!permisoOk) permLauncher.launch(Manifest.permission.CAMERA) }

    val captura = remember { ImageCapture.Builder().build() }
    var archivo by remember { mutableStateOf<File?>(null) }
    var vista by remember { mutableStateOf<Bitmap?>(null) }
    var procesando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (archivo == null) "Foto del comprobante" else "¿Se lee bien?") },
                navigationIcon = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            if (!permisoOk) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Se necesita permiso de cámara para fotografiar el comprobante.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Dar permiso")
                    }
                }
                return@Column
            }

            val bmp = vista
            if (archivo == null || bmp == null) {
                // ---------------- Etapa 1: capturar ----------------
                Text(
                    "Pide al cliente que muestre el comprobante en su pantalla y " +
                            "encuadra monto, fecha y destinatario.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            val pv = PreviewView(context)
                            val future = ProcessCameraProvider.getInstance(context)
                            future.addListener({
                                val provider = future.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(pv.surfaceProvider)
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    life, CameraSelector.DEFAULT_BACK_CAMERA, preview, captura,
                                )
                            }, ContextCompat.getMainExecutor(context))
                            pv
                        },
                    )
                    if (procesando) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(color = Color.White) }
                    }
                }
                error?.let {
                    Text(
                        it,
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    enabled = !procesando,
                    onClick = {
                        procesando = true; error = null
                        val destino = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
                        val opts = ImageCapture.OutputFileOptions.Builder(destino).build()
                        captura.takePicture(
                            opts,
                            ContextCompat.getMainExecutor(ctx),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                                    scope.launch {
                                        try {
                                            val listo = withContext(Dispatchers.IO) {
                                                val chico = File(
                                                    ctx.cacheDir,
                                                    "comp_${System.currentTimeMillis()}.jpg",
                                                )
                                                Fotos.comprimir(destino, chico)
                                                destino.delete()
                                                chico to Fotos.decodificar(chico)
                                            }
                                            archivo = listo.first
                                            vista = listo.second
                                        } catch (e: Exception) {
                                            error = e.message ?: "No se pudo procesar la foto"
                                        } finally {
                                            procesando = false
                                        }
                                    }
                                }

                                override fun onError(e: ImageCaptureException) {
                                    error = e.message ?: "La cámara no pudo tomar la foto"
                                    procesando = false
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                ) { Text("Tomar foto") }

            } else {
                // ---------------- Etapa 2: revisar ----------------
                var escala by remember { mutableStateOf(1f) }
                var desplaz by remember { mutableStateOf(Offset.Zero) }

                Text(
                    "Revisa que se lean el monto, la fecha y el destinatario. " +
                            "Puedes acercar con dos dedos.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                escala = (escala * zoom).coerceIn(1f, 6f)
                                desplaz = if (escala <= 1f) Offset.Zero else desplaz + pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Comprobante de transferencia",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = escala,
                                scaleY = escala,
                                translationX = desplaz.x,
                                translationY = desplaz.y,
                            ),
                    )
                }

                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { archivo?.let(onListo) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Se lee bien, continuar") }
                    OutlinedButton(
                        onClick = {
                            archivo?.delete()
                            archivo = null
                            // Sin recycle(): Compose puede seguir dibujando este
                            // bitmap durante la recomposicion y reventaria.
                            vista = null
                            escala = 1f
                            desplaz = Offset.Zero
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Repetir foto") }
                }
            }
        }
    }
}
