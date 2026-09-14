package cl.efficientchile.inventario.ui

import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cl.efficientchile.inventario.data.Dinero
import cl.efficientchile.inventario.util.Fotos
import cl.efficientchile.inventario.util.LectorBoleta
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fotografiar la boleta y precargar lo que dice.
 *
 * Dos pantallas en una: primero la camara, despues lo que se leyo para que el
 * vendedor lo confirme. Nunca se salta la confirmacion, y ese es el punto:
 * un numero precargado que nadie revisa es peor que un campo vacio, porque el
 * campo vacio al menos se nota.
 *
 * El reconocimiento corre en el propio telefono. No sube la foto a ningun
 * lado para leerla, no cuesta por uso, y funciona sin señal, que en un vivero
 * pasa seguido.
 */
@Composable
fun OcrScreen(
    totalEsperado: Double,
    onListo: (LectorBoleta.Lectura, File) -> Unit,
    onCancelar: () -> Unit,
) {
    val ctx = LocalContext.current
    val dueno = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var captura by remember { mutableStateOf<ImageCapture?>(null) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lectura by remember { mutableStateOf<LectorBoleta.Lectura?>(null) }
    var archivo by remember { mutableStateOf<File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (lectura == null) "Fotografía la boleta" else "Lo que se leyó") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Marino, titleContentColor = Blanco),
                actions = {
                    TextButton(onClick = onCancelar) {
                        Text("Cancelar", color = Blanco)
                    }
                },
            )
        },
    ) { pad ->
        val L = lectura
        if (L == null) {
            // ------------------------------------------------------- camara
            Column(Modifier.padding(pad).fillMaxSize()) {
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
                                val cap = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .build()
                                captura = cap
                                proveedor.unbindAll()
                                proveedor.bindToLifecycle(
                                    dueno, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
                            }, ContextCompat.getMainExecutor(c))
                            vista
                        },
                    )
                    if (trabajando) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Blanco)
                                Spacer(Modifier.height(12.dp))
                                Text("Leyendo la boleta…", color = Blanco)
                            }
                        }
                    }
                }

                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Apoya la boleta en algo plano, que entre entera y sin sombra encima. " +
                            "Da lo mismo si sale algo torcida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TintaSuave,
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = !trabajando && captura != null,
                        onClick = {
                            trabajando = true; error = null
                            val crudo = File(ctx.cacheDir, "bol_${System.currentTimeMillis()}.jpg")
                            captura!!.takePicture(
                                ImageCapture.OutputFileOptions.Builder(crudo).build(),
                                ContextCompat.getMainExecutor(ctx),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                                        scope.launch {
                                            try {
                                                val (chico, texto) = withContext(Dispatchers.IO) {
                                                    val c = File(
                                                        ctx.cacheDir,
                                                        "comp_${System.currentTimeMillis()}.jpg")
                                                    Fotos.comprimir(crudo, c)
                                                    crudo.delete()
                                                    c to reconocer(c)
                                                }
                                                archivo = chico
                                                lectura = LectorBoleta.leer(texto)
                                            } catch (e: Exception) {
                                                error = e.message ?: "No se pudo leer la foto"
                                            } finally {
                                                trabajando = false
                                            }
                                        }
                                    }

                                    override fun onError(e: ImageCaptureException) {
                                        error = e.message ?: "La cámara no pudo tomar la foto"
                                        trabajando = false
                                    }
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Tomar la foto", style = MaterialTheme.typography.labelLarge) }
                }
            }
        } else {
            // ------------------------------------------------ confirmacion
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                /* El descuadre contra el total de la venta es el aviso que de
                   verdad salva plata: si la boleta dice otra cosa que el
                   carrito, alguien cobro de menos o escaneo de mas.

                   El total se saca a una variable local en vez de usarlo
                   directo: el compilador no siempre acepta el smart cast de
                   una propiedad, y eso revienta el build sin avisar antes. */
                val totalLeido = L.total
                val descuadre = totalLeido != null &&
                    kotlin.math.abs(totalLeido - totalEsperado) > 2

                if (descuadre && totalLeido != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("La boleta no coincide con la venta",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error)
                            Text(
                                "La boleta dice ${Dinero.clp(totalLeido.toDouble())} y la venta " +
                                    "suma ${Dinero.clp(totalEsperado)}. Revisa antes de seguir.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Fila("Número", L.numero ?: "no se encontró", L.numero == null)
                Fila("Total", L.total?.let { Dinero.clp(it.toDouble()) } ?: "no se encontró",
                    L.total == null)
                Fila("Neto", L.neto?.let { Dinero.clp(it.toDouble()) } ?: "—", false)
                Fila("IVA", L.iva?.let { Dinero.clp(it.toDouble()) } ?: "—", false)
                if (L.fecha != null) Fila("Fecha", "${L.fecha} ${L.hora ?: ""}".trim(), false)
                if (L.rut != null) Fila("RUT", L.rut, false)
                if (L.ultimos4 != null) Fila("Tarjeta", "•••• ${L.ultimos4}", false)

                L.avisos.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                }

                Text(
                    "Estos datos se copian al formulario y los puedes corregir ahí. " +
                        "Nada se guarda todavía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TintaSuave,
                )

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { archivo?.let { onListo(L, it) } },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Usar estos datos", style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = { lectura = null; archivo?.delete(); archivo = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Repetir la foto") }
            }
        }
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String?, falta: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, color = TintaSuave)
        Text(
            valor ?: "—",
            fontWeight = FontWeight.SemiBold,
            color = if (falta) MaterialTheme.colorScheme.error else Tinta,
        )
    }
}

/** Texto crudo de la foto. Corre en el telefono, sin red y sin costo por uso. */
private suspend fun reconocer(foto: File): String {
    val bmp = BitmapFactory.decodeFile(foto.absolutePath)
        ?: throw IllegalStateException("No se pudo abrir la foto")
    val lector = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        lector.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { cont.resumeWith(Result.success(it.text)) }
            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}
