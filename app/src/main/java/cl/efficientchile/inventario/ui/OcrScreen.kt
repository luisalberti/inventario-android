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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cl.efficientchile.inventario.data.Dinero
import cl.efficientchile.inventario.util.Fotos
import cl.efficientchile.inventario.util.LectorBoleta
import com.google.mlkit.vision.common.InputImage
// Con alias: sin el, este Text tapa al Text de Compose en todo el archivo.
import com.google.mlkit.vision.text.Text as TextoOcr
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
    var verTexto by remember { mutableStateOf(false) }

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
        bottomBar = {
            /* Los botones de la confirmacion van fijos abajo y no al final
               del scroll: asi se ven siempre, sin tener que adivinar que hay
               que deslizar. navigationBarsPadding los deja por encima de los
               botones de navegacion de Android. */
            val leida = lectura
            if (leida != null) {
                Surface(shadowElevation = 8.dp, color = Blanco) {
                    Column(
                        Modifier.navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { archivo?.let { onListo(leida, it) } },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Usar estos datos", style = MaterialTheme.typography.labelLarge) }
                        OutlinedButton(
                            onClick = {
                                lectura = null; archivo?.delete(); archivo = null; verTexto = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Repetir la foto") }
                    }
                }
            }
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
                                                val (chico, leido) = withContext(Dispatchers.IO) {
                                                    val c = File(
                                                        ctx.cacheDir,
                                                        "comp_${System.currentTimeMillis()}.jpg")
                                                    Fotos.comprimir(crudo, c)
                                                    crudo.delete()
                                                    c to reconocer(c)
                                                }
                                                archivo = chico
                                                lectura = LectorBoleta.leer(
                                                    texto = leido.bloques,
                                                    textoPorFilas = leido.filas,
                                                )
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
                    abs(totalLeido - totalEsperado) > 2

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

                Fila("Número", L.numero ?: "no se encontró", falta = L.numero == null)
                Fila("Total", L.total?.let { Dinero.clp(it.toDouble()) } ?: "no se encontró",
                    falta = L.total == null, calculado = "total" in L.calculados)
                Fila("Neto", L.neto?.let { Dinero.clp(it.toDouble()) } ?: "—",
                    calculado = "neto" in L.calculados)
                Fila("IVA", L.iva?.let { Dinero.clp(it.toDouble()) } ?: "—",
                    calculado = "iva" in L.calculados)
                if (L.fecha != null) Fila("Fecha", "${L.fecha} ${L.hora ?: ""}".trim())
                if (L.rut != null) Fila("RUT", L.rut)
                if (L.ultimos4 != null) Fila("Tarjeta", "•••• ${L.ultimos4}")

                L.avisos.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                }

                Text(
                    "Estos datos se copian al formulario y los puedes corregir ahí. " +
                        "Nada se guarda todavía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TintaSuave,
                )

                /* Diagnostico. Cuando un monto no sale, lo primero es ver que
                   leyo el telefono: si el numero no esta en este texto, el
                   problema es la foto; si esta, es el lector. Se puede
                   seleccionar y copiar para mandarlo. */
                TextButton(
                    onClick = { verTexto = !verTexto },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (verTexto) "Ocultar el texto leído" else "Ver el texto que leyó el teléfono")
                }
                if (verTexto) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SelectionContainer {
                            Text(
                                L.textoLeido.ifBlank { "(no se leyó texto)" },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Una fila etiqueta / valor. Si el valor no venia en el papel y se calculo
 * con la formula, lo dice debajo en chico: el vendedor tiene que saber que
 * numero leyo la camara y cual puso la cuenta.
 */
@Composable
private fun Fila(
    etiqueta: String,
    valor: String?,
    falta: Boolean = false,
    calculado: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, color = TintaSuave)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                valor ?: "—",
                fontWeight = FontWeight.SemiBold,
                color = if (falta) MaterialTheme.colorScheme.error else Tinta,
            )
            if (calculado) {
                Text("calculado", style = MaterialTheme.typography.bodySmall, color = TintaSuave)
            }
        }
    }
}

/** El texto de la foto de dos formas: como lo agrupa el OCR y por filas. */
private class TextoLeido(val bloques: String, val filas: String)

/** Texto crudo de la foto. Corre en el telefono, sin red y sin costo por uso. */
private suspend fun reconocer(foto: File): TextoLeido {
    val bmp = BitmapFactory.decodeFile(foto.absolutePath)
        ?: throw IllegalStateException("No se pudo abrir la foto")
    val lector = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        lector.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener {
                cont.resumeWith(Result.success(TextoLeido(it.text, enFilas(it))))
            }
            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}

/** Una linea del OCR con su posicion ya enderezada. */
private class Trozo(val texto: String, val x: Double, val y: Double, val alto: Double)

/**
 * Reordena el texto por filas segun donde esta cada linea en la foto.
 *
 * El OCR agrupa por bloques: si NETO / IVA / TOTAL estan en una columna y los
 * montos en otra, entrega "NETO IVA TOTAL" juntos y los tres montos despues,
 * y ninguna linea trae etiqueta y monto a la vez. Aca se vuelven a armar las
 * filas como se ven en el papel: "NETO   $10.404".
 *
 * Dos cosas para que funcione con fotos reales:
 * - La foto sale algo torcida. Se mide la inclinacion tipica de las lineas y
 *   se endereza antes de comparar alturas.
 * - El monto casi nunca queda exactamente a la altura de su etiqueta. Dos
 *   lineas van en la misma fila si sus centros estan a menos de 60% del alto
 *   de una linea. Lo que aun asi quede corrido una fila, lo resuelve
 *   LectorBoleta mirando la fila de arriba y la de abajo y comprobando la
 *   cuenta neto + IVA = total.
 */
private fun enFilas(res: TextoOcr): String {
    val lineas = res.textBlocks.flatMap { it.lines }
    if (lineas.isEmpty()) return res.text

    // Inclinacion tipica: la mediana del angulo de las lineas largas.
    val angulos = lineas.mapNotNull { l ->
        val p = l.cornerPoints
        if (p == null || p.size < 4) return@mapNotNull null
        val dx = (p[1].x - p[0].x).toDouble()
        val dy = (p[1].y - p[0].y).toDouble()
        if (hypot(dx, dy) < 40.0) null else atan2(dy, dx)
    }.sorted()
    val angulo = if (angulos.isEmpty()) 0.0 else angulos[angulos.size / 2]
    val c = cos(-angulo)
    val s = sin(-angulo)

    val trozos = lineas.mapNotNull { l ->
        val p = l.cornerPoints
        if (p != null && p.size >= 4) {
            val cx = (p[0].x + p[1].x + p[2].x + p[3].x) / 4.0
            val cy = (p[0].y + p[1].y + p[2].y + p[3].y) / 4.0
            val izqX = (p[0].x + p[3].x) / 2.0
            val izqY = (p[0].y + p[3].y) / 2.0
            val alto = hypot((p[3].x - p[0].x).toDouble(), (p[3].y - p[0].y).toDouble())
            Trozo(l.text, izqX * c - izqY * s, cx * s + cy * c, alto)
        } else {
            val b = l.boundingBox ?: return@mapNotNull null
            Trozo(l.text, b.left.toDouble(), b.exactCenterY().toDouble(), b.height().toDouble())
        }
    }.sortedBy { it.y }

    val filas = mutableListOf<MutableList<Trozo>>()
    var yFila = 0.0
    var altoFila = 0.0
    for (t in trozos) {
        val actual = filas.lastOrNull()
        val margen = 0.6 * maxOf(t.alto, altoFila)
        if (actual != null && abs(t.y - yFila) <= margen) {
            actual += t
            yFila = actual.sumOf { it.y } / actual.size
            altoFila = actual.sumOf { it.alto } / actual.size
        } else {
            filas.add(mutableListOf(t))
            yFila = t.y
            altoFila = t.alto
        }
    }

    return filas.joinToString("\n") { fila ->
        fila.sortedBy { it.x }.joinToString("   ") { it.texto }
    }
}
