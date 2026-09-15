package cl.efficientchile.inventario.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.efficientchile.inventario.data.Dinero
import cl.efficientchile.inventario.data.ItemVenta
import cl.efficientchile.inventario.data.LineaCarrito
import cl.efficientchile.inventario.data.Repo
import cl.efficientchile.inventario.data.VentaReq
import cl.efficientchile.inventario.data.VentaResp
import cl.efficientchile.inventario.util.Campanita
import cl.efficientchile.inventario.util.Formato
import cl.efficientchile.inventario.util.LectorBoleta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_POR_LINEA = 999

/**
 * La venta, como un proceso y no como un formulario.
 *
 * Antes esto era una sola pantalla con quince campos, de los cuales la mitad
 * no aplicaban nunca, y cuatro pantallas de scroll. El vendedor no sabia en
 * que parte iba ni que le faltaba, y se enteraba de lo que faltaba recien
 * cuando apretaba Confirmar y le salia un error rojo.
 *
 * Ahora cada paso aparece cuando el anterior esta resuelto, y hay UN boton
 * abajo, siempre a la vista, que dice lo que toca hacer ahora. Cuando falta
 * algo, el boton lo dice con todas sus letras en vez de quedarse gris.
 *
 * El paso no avanza solo al elegir un chip: avanza cuando el vendedor aprieta
 * el boton. Es la diferencia entre una pantalla que acompaña y una que se
 * adelanta y hace perder el hilo.
 */
internal enum class Paso { CARRITO, DOCUMENTO, EMPRESA, PAGO, DOCUMENTO_NUM, LISTO }

/**
 * Lo que el vendedor ya eligio y escribio en la venta en curso.
 *
 * Se crea en AppRoot y no dentro de SaleScreen. Al abrir la camara para la
 * boleta, SaleScreen sale de pantalla, y todo lo que guardaba con remember se
 * borraba: al volver preguntaba otra vez boleta o factura y la forma de pago.
 * Tambien se perdia la lectura, y la venta se registraba como "manual"
 * aunque el numero hubiera salido de la foto.
 */
class VentaEnCurso {
    internal val paso = mutableStateOf(Paso.CARRITO)
    val tipoDoc = mutableStateOf<String?>(null)
    val formaPago = mutableStateOf<String?>(null)
    val monto = mutableStateOf("")
    val numDoc = mutableStateOf("")
    val bancoOp = mutableStateOf("")
    val rut = mutableStateOf("")
    val razon = mutableStateOf("")
    val direccion = mutableStateOf("")
    val giro = mutableStateOf("")
    /** La lectura de boleta que relleno el formulario. Va al servidor como origen "ocr". */
    val lectura = mutableStateOf<LectorBoleta.Lectura?>(null)

    /** Deja la venta en blanco: al confirmar, cancelar o vaciar el carrito. */
    fun reiniciar() {
        paso.value = Paso.CARRITO
        tipoDoc.value = null
        formaPago.value = null
        monto.value = ""
        numDoc.value = ""
        bancoOp.value = ""
        rut.value = ""
        razon.value = ""
        direccion.value = ""
        giro.value = ""
        lectura.value = null
    }
}

@Composable
fun SaleScreen(
    venta: VentaEnCurso,
    repo: Repo,
    baseUrl: String,
    token: String,
    carrito: List<LineaCarrito>,
    comprobante: File?,
    onCambiarCantidad: (Int, Int) -> Unit,
    onQuitarLinea: (Int) -> Unit,
    onAgregarOtro: () -> Unit,
    onTomarComprobante: () -> Unit,
    onQuitarComprobante: () -> Unit,
    onEscanearBoleta: () -> Unit,
    lecturaBoleta: LectorBoleta.Lectura?,
    onLecturaUsada: () -> Unit,
    onConfirmado: () -> Unit,
    onCancelar: () -> Unit,
) {
    val total = carrito.sumOf { it.subtotal }
    val neto = Dinero.neto(total)
    val iva = Dinero.iva(total)
    val unidades = carrito.sumOf { it.cantidad }

    var paso by venta.paso
    var tipoDoc by venta.tipoDoc
    var formaPago by venta.formaPago
    var monto by venta.monto
    var numDoc by venta.numDoc
    var bancoOp by venta.bancoOp
    var rut by venta.rut
    var razon by venta.razon
    var direccion by venta.direccion
    var giro by venta.giro

    var enviando by remember { mutableStateOf(false) }
    var estado by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf<VentaResp?>(null) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    /* Lo que leyo la camara entra a los campos una sola vez, y solo donde
       todavia no hay nada escrito: si el vendedor ya corrigio el numero a
       mano, una segunda foto no se lo pisa. */
    LaunchedEffect(lecturaBoleta) {
        val l = lecturaBoleta ?: return@LaunchedEffect
        if (numDoc.isBlank()) l.numero?.let { numDoc = Formato.documento(it) }
        if (rut.isBlank()) l.rut?.let { rut = Formato.rut(it) }
        venta.lectura.value = l
        onLecturaUsada()
    }

    /* Lo nuevo aparece abajo, asi que la pantalla baja sola hasta ahi. Antes
       el paso siguiente se abria fuera de la vista y habia que adivinar que
       tocaba deslizar. Se espera lo que dura la animacion de Revelado, si no
       se baja hasta donde la seccion todavia no mide su alto final. */
    LaunchedEffect(paso, tipoDoc, formaPago) {
        if (paso == Paso.CARRITO) return@LaunchedEffect
        delay(350)
        scroll.animateScrollTo(scroll.maxValue)
    }

    exito?.let { v ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Venta registrada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        v.numeroTransaccion ?: "#${v.id}",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(Dinero.clp(v.total ?: total),
                        style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Neto ${Dinero.clp(v.neto ?: neto)} · IVA ${Dinero.clp(v.iva ?: iva)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TintaSuave,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { exito = null; onConfirmado() }) { Text("Listo") }
            },
        )
    }

    val falta = queFalta(paso, tipoDoc, formaPago, monto, total, numDoc, rut, razon,
                         direccion, giro, comprobante)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tituloDe(paso)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Marino, titleContentColor = Blanco),
                )
                /* La barra de avance va sobre el marino y no sobre el blanco.
                   Medido: cyan sobre blanco da 2,8 a 1 y se pierde; sobre el
                   marino da 5,5 a 1 y se ve a un brazo de distancia. */
                /* Sin drawStopIndicator: ese parametro llego en Material 3
                   1.3.0 y el BOM fijado trae la 1.2.1. Compilar contra una
                   firma que no existe es el tipo de error que solo aparece en
                   el build, veinte minutos despues de subir el codigo. */
                LinearProgressIndicator(
                    progress = { avanceDe(paso, tipoDoc) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Cyan,
                    trackColor = MarinoSuave,
                )
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Blanco) {
                /* navigationBarsPadding: la app dibuja de borde a borde
                   (enableEdgeToEdge) y sin esto "Volver atrás" quedaba debajo
                   de los botones de navegacion de Android. */
                Column(
                    Modifier.navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (paso != Paso.LISTO) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Total", color = TintaSuave)
                            Text(Dinero.clp(total),
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (enviando && estado.isNotBlank()) {
                        Text(estado, style = MaterialTheme.typography.bodySmall,
                            color = TintaSuave)
                    }

                    /* Cuando falta algo, el boton lo DICE. Un boton gris sin
                       explicacion obliga a buscar el campo vacio a ojo, y en
                       un formulario largo eso es exactamente lo que hacia
                       perder el tiempo con un cliente adelante. */
                    if (falta != null) {
                        Text(falta, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }

                    if (paso == Paso.CARRITO) {
                        // Lo que el vendedor hace despues de escanear el
                        // primer producto, casi siempre, es escanear el
                        // segundo. Ese es el boton destacado.
                        Button(
                            onClick = onAgregarOtro,
                            enabled = !enviando,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Text("Escanear otro producto",
                                style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = { paso = Paso.DOCUMENTO },
                            enabled = !enviando && carrito.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) { Text("Listo, ir a cobrar") }
                    } else {
                        Button(
                            onClick = {
                                if (paso == Paso.LISTO) {
                                    enviar(
                                        scope, repo, baseUrl, token, carrito, tipoDoc!!,
                                        formaPago!!, monto, numDoc, bancoOp, rut, razon,
                                        direccion, giro, comprobante, venta.lectura.value,
                                        onEstado = { estado = it },
                                        onEnviando = { enviando = it },
                                        onError = { error = it },
                                        onExito = { exito = it },
                                    )
                                } else {
                                    paso = siguiente(paso, tipoDoc)
                                }
                            },
                            enabled = !enviando && falta == null,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            if (enviando) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp), strokeWidth = 2.dp, color = Blanco)
                            } else {
                                Text(
                                    if (paso == Paso.LISTO) "Confirmar venta ${Dinero.clp(total)}"
                                    else "Continuar",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        TextButton(
                            onClick = { paso = anterior(paso, tipoDoc) },
                            enabled = !enviando,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Volver atrás") }
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ------------------------------------------------------ carrito
            Seccion("Productos", "$unidades ${if (unidades == 1) "unidad" else "unidades"}") {
                if (carrito.isEmpty()) {
                    Text("Todavía no escaneas nada.", color = TintaSuave)
                }
                carrito.forEachIndexed { i, linea ->
                    LineaCarritoFila(
                        linea = linea,
                        habilitado = !enviando && paso == Paso.CARRITO,
                        onMenos = { onCambiarCantidad(i, linea.cantidad - 1) },
                        onMas = { onCambiarCantidad(i, linea.cantidad + 1) },
                        onQuitar = { onQuitarLinea(i) },
                    )
                    if (i < carrito.lastIndex) HorizontalDivider()
                }
                if (carrito.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    FilaTotal("Neto", Dinero.clp(neto))
                    FilaTotal("IVA (19%)", Dinero.clp(iva))
                }
            }

            // --------------------------------------------------- documento
            Revelado(paso >= Paso.DOCUMENTO) {
                Seccion("¿Boleta o factura?", null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OpcionGrande("Boleta", tipoDoc == "boleta",
                            Modifier.weight(1f)) { tipoDoc = "boleta" }
                        OpcionGrande("Factura", tipoDoc == "factura",
                            Modifier.weight(1f)) { tipoDoc = "factura" }
                    }
                    if (tipoDoc == "factura") {
                        Text(
                            "Vas a necesitar el RUT, la razón social, la dirección y el giro " +
                                "de la empresa.",
                            style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                    }
                }
            }

            // ----------------------------------------------------- empresa
            Revelado(paso >= Paso.EMPRESA && tipoDoc == "factura") {
                Seccion("Datos de la empresa", null) {
                    val rutMalo = rut.isNotBlank() && !Formato.rutValido(rut)
                    OutlinedTextField(
                        rut, { rut = Formato.rut(it) },
                        label = { Text("RUT") }, placeholder = { Text("76.853.513-2") },
                        isError = rutMalo,
                        supportingText = if (rutMalo) {
                            { Text("El dígito verificador no calza.") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        razon, { razon = Formato.nombrePropio(it) },
                        label = { Text("Razón social") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        direccion, { direccion = Formato.nombrePropio(it) },
                        label = { Text("Dirección comercial") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        giro, { giro = it }, label = { Text("Giro") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // -------------------------------------------------------- pago
            Revelado(paso >= Paso.PAGO) {
                Seccion("¿Cómo paga?", null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OpcionGrande("Efectivo", formaPago == "efectivo",
                            Modifier.weight(1f)) { formaPago = "efectivo" }
                        OpcionGrande("Tarjeta", formaPago == "tarjeta",
                            Modifier.weight(1f)) { formaPago = "tarjeta" }
                        OpcionGrande("Transfer.", formaPago == "transferencia",
                            Modifier.weight(1f)) { formaPago = "transferencia" }
                    }

                    if (formaPago == "efectivo") {
                        OutlinedTextField(
                            monto, { monto = Formato.monto(it) },
                            label = { Text("¿Con cuánto paga?") },
                            prefix = { Text("$") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        val recibido = Formato.montoValor(monto) ?: 0.0
                        if (recibido >= total && total > 0) {
                            // El vuelto en grande: es el numero que el
                            // vendedor tiene que leer de un vistazo mientras
                            // cuenta billetes.
                            Surface(
                                color = CyanPalido, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Vuelto", color = TintaSuave)
                                    Text(Dinero.clp(recibido - total),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = Marino)
                                }
                            }
                        } else if (monto.isNotBlank()) {
                            Text("Faltan ${Dinero.clp(total - recibido)}",
                                color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (formaPago == "tarjeta") {
                        OutlinedTextField(
                            bancoOp, { bancoOp = Formato.documento(it) },
                            label = { Text("N° de operación (opcional)") },
                            supportingText = {
                                Text("El segundo número del voucher, si el papel trae dos.")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (formaPago == "transferencia") {
                        Text(
                            "Vas a tener que fotografiar el comprobante en el paso siguiente.",
                            style = MaterialTheme.typography.bodySmall, color = TintaSuave)
                    }
                }
            }

            // ------------------------------------- numero del documento + foto
            Revelado(paso >= Paso.DOCUMENTO_NUM) {
                Seccion("El papel", null) {
                    Button(
                        onClick = onEscanearBoleta,
                        enabled = !enviando,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanProfundo),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Fotografiar la boleta y leerla") }
                    Text(
                        "La foto rellena el número y el total. Siempre te muestra lo que leyó " +
                            "antes de usarlo.",
                        style = MaterialTheme.typography.bodySmall, color = TintaSuave)

                    OutlinedTextField(
                        numDoc, { numDoc = Formato.documento(it) },
                        label = { Text("N° de boleta o comprobante") },
                        placeholder = { Text("000123") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )

                    if (formaPago != "efectivo") {
                        val obligatoria = formaPago == "transferencia"
                        Surface(
                            color = if (comprobante != null) CyanPalido
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (comprobante == null) {
                                    Text(
                                        if (obligatoria) "Falta la foto del comprobante"
                                        else "Foto del voucher (opcional)",
                                        fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (obligatoria)
                                            "Fotografía la pantalla del cliente con la " +
                                                "transferencia hecha."
                                        else
                                            "Si después hay un reclamo, es la única prueba de " +
                                                "que el cobro se hizo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TintaSuave)
                                    OutlinedButton(
                                        onClick = onTomarComprobante, enabled = !enviando,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Tomar la foto") }
                                } else {
                                    Text("Comprobante listo", fontWeight = FontWeight.SemiBold,
                                        color = Bien)
                                    OutlinedButton(
                                        onClick = onQuitarComprobante, enabled = !enviando,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Repetir la foto") }
                                }
                            }
                        }
                    }
                }
            }

            Revelado(paso >= Paso.LISTO) {
                Seccion("Revisa antes de confirmar", null) {
                    Resumen("Productos", "$unidades")
                    Resumen("Documento", tipoDoc?.replaceFirstChar { it.uppercase() } ?: "—")
                    Resumen("N°", numDoc.ifBlank { "—" })
                    Resumen("Pago", formaPago?.replaceFirstChar { it.uppercase() } ?: "—")
                    if (formaPago == "efectivo") {
                        val rec = Formato.montoValor(monto) ?: 0.0
                        Resumen("Vuelto", Dinero.clp((rec - total).coerceAtLeast(0.0)))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Resumen("Total", Dinero.clp(total), destacado = true)
                }
            }

            TextButton(onClick = onCancelar, enabled = !enviando,
                modifier = Modifier.fillMaxWidth()) { Text("Cancelar la venta") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------------ pasos

private fun tituloDe(p: Paso) = when (p) {
    Paso.CARRITO -> "Productos"
    Paso.DOCUMENTO -> "Documento"
    Paso.EMPRESA -> "Empresa"
    Paso.PAGO -> "Pago"
    Paso.DOCUMENTO_NUM -> "El papel"
    Paso.LISTO -> "Confirmar"
}

/** Cuanto del proceso va cumplido. La factura agrega un paso. */
private fun avanceDe(p: Paso, tipoDoc: String?): Float {
    val totalPasos = if (tipoDoc == "factura") 5f else 4f
    val hecho = when (p) {
        Paso.CARRITO -> 0f
        Paso.DOCUMENTO -> 1f
        Paso.EMPRESA -> 2f
        Paso.PAGO -> if (tipoDoc == "factura") 3f else 2f
        Paso.DOCUMENTO_NUM -> if (tipoDoc == "factura") 4f else 3f
        Paso.LISTO -> totalPasos
    }
    return hecho / totalPasos
}

/* El paso de empresa solo existe con factura. Saltarlo aca, en un solo lugar,
   evita tener que preguntar "y si es boleta" en cada pantalla. */
private fun siguiente(p: Paso, tipoDoc: String?): Paso = when (p) {
    Paso.CARRITO -> Paso.DOCUMENTO
    Paso.DOCUMENTO -> if (tipoDoc == "factura") Paso.EMPRESA else Paso.PAGO
    Paso.EMPRESA -> Paso.PAGO
    Paso.PAGO -> Paso.DOCUMENTO_NUM
    Paso.DOCUMENTO_NUM -> Paso.LISTO
    Paso.LISTO -> Paso.LISTO
}

private fun anterior(p: Paso, tipoDoc: String?): Paso = when (p) {
    Paso.CARRITO -> Paso.CARRITO
    Paso.DOCUMENTO -> Paso.CARRITO
    Paso.EMPRESA -> Paso.DOCUMENTO
    Paso.PAGO -> if (tipoDoc == "factura") Paso.EMPRESA else Paso.DOCUMENTO
    Paso.DOCUMENTO_NUM -> Paso.PAGO
    Paso.LISTO -> Paso.DOCUMENTO_NUM
}

/**
 * Que falta para poder avanzar, en palabras.
 *
 * Devuelve null cuando no falta nada. Lo que devuelve se muestra tal cual
 * sobre el boton, asi que tiene que nombrar el campo, no describir un estado.
 *
 * Lo que NO bloquea: un RUT con el verificador malo. El campo lo avisa en
 * rojo y ahi queda. Hay empresas extranjeras y casos raros, y dejar a un
 * vendedor con un cliente adelante sin poder cerrar la venta porque el
 * sistema no le cree el RUT es mas caro que un RUT mal escrito en la base.
 */
private fun queFalta(
    paso: Paso, tipoDoc: String?, formaPago: String?, monto: String, total: Double,
    numDoc: String, rut: String, razon: String, direccion: String, giro: String,
    comprobante: File?,
): String? = when (paso) {
    Paso.CARRITO -> null
    Paso.DOCUMENTO -> if (tipoDoc == null) "Elige boleta o factura" else null
    Paso.EMPRESA -> when {
        rut.isBlank() -> "Falta el RUT de la empresa"
        razon.isBlank() -> "Falta la razón social"
        direccion.isBlank() -> "Falta la dirección comercial"
        giro.isBlank() -> "Falta el giro"
        else -> null
    }
    Paso.PAGO -> when {
        formaPago == null -> "Elige la forma de pago"
        formaPago == "efectivo" && Formato.montoValor(monto) == null ->
            "Escribe con cuánto paga"
        formaPago == "efectivo" && (Formato.montoValor(monto) ?: 0.0) < total ->
            "El monto recibido no alcanza"
        else -> null
    }
    Paso.DOCUMENTO_NUM -> when {
        numDoc.isBlank() -> "Falta el número de boleta o comprobante"
        formaPago == "transferencia" && comprobante == null ->
            "La transferencia necesita la foto del comprobante"
        else -> null
    }
    Paso.LISTO -> null
}

// ------------------------------------------------------------------ envio

private fun enviar(
    scope: kotlinx.coroutines.CoroutineScope,
    repo: Repo, baseUrl: String, token: String,
    carrito: List<LineaCarrito>, tipoDoc: String, formaPago: String,
    monto: String, numDoc: String, bancoOp: String,
    rut: String, razon: String, direccion: String, giro: String,
    comprobante: File?, lectura: LectorBoleta.Lectura?,
    onEstado: (String) -> Unit, onEnviando: (Boolean) -> Unit,
    onError: (String?) -> Unit, onExito: (VentaResp) -> Unit,
) {
    onEnviando(true); onError(null)
    scope.launch {
        try {
            // La foto se sube en cualquier forma de pago que la tenga, no
            // solo en transferencia: antes una foto tomada con tarjeta se
            // descartaba en silencio al confirmar.
            val tokenComp = if (comprobante != null) {
                onEstado("Subiendo comprobante…")
                repo.subirComprobante(baseUrl, token, comprobante).token
            } else null

            onEstado("Registrando venta…")
            val req = VentaReq(
                items = carrito.map { ItemVenta(it.uids, it.cantidad) },
                tipoDocumento = tipoDoc,
                formaPago = formaPago,
                montoPagado = if (formaPago == "efectivo") Formato.montoValor(monto) else null,
                numeroDocumento = numDoc.ifBlank { null },
                bancoOperacion = bancoOp.ifBlank { null },
                comprobanteToken = tokenComp,
                // De donde salio el numero: tecleado o leido de la foto. Sirve
                // para saber despues si vale la pena confiar en el lector.
                cobroOrigen = if (lectura != null) "ocr" else "manual",
                cobroUltimos4 = lectura?.ultimos4,
                rutEmpresa = if (tipoDoc == "factura") rut else null,
                razonSocial = if (tipoDoc == "factura") razon else null,
                direccionComercial = if (tipoDoc == "factura") direccion else null,
                giro = if (tipoDoc == "factura") giro else null,
            )
            val resp = repo.crearVenta(baseUrl, token, req)
            Campanita.sonar()
            onExito(resp)
        } catch (e: Exception) {
            onError(e.message ?: "Error al registrar la venta")
        } finally {
            onEnviando(false); onEstado("")
        }
    }
}

// --------------------------------------------------------------- pedacitos

@Composable
private fun Revelado(visible: Boolean, contenido: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically()) { contenido() }
}

@Composable
private fun Seccion(titulo: String, apunte: String?, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(color = Blanco, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                apunte?.let { Text(it, color = TintaSuave,
                    style = MaterialTheme.typography.bodySmall) }
            }
            contenido()
        }
    }
}

/**
 * Una opcion grande, de las que se aprietan con el pulgar.
 *
 * No es un FilterChip de Material: el chip apretaba el texto y dejaba
 * "Transferencia" partida en dos lineas como "Transferen / cia". Esto reserva
 * la altura y encoge la letra antes que cortar la palabra.
 */
@Composable
private fun OpcionGrande(
    texto: String, elegido: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (elegido) CyanPalido else Blanco,
        border = androidx.compose.foundation.BorderStroke(
            if (elegido) 2.dp else 1.dp, if (elegido) CyanProfundo else Borde),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.height(52.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                texto,
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = if (elegido) Marino else Tinta,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Resumen(etiqueta: String, valor: String, destacado: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = TintaSuave)
        Text(valor,
            style = if (destacado) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LineaCarritoFila(
    linea: LineaCarrito, habilitado: Boolean,
    onMenos: () -> Unit, onMas: () -> Unit, onQuitar: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(linea.nombre, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("SKU ${linea.sku} · ${Dinero.clp(linea.precioVenta)} c/u",
                    style = MaterialTheme.typography.bodySmall, color = TintaSuave)
            }
            if (habilitado) {
                IconButton(onClick = onQuitar) {
                    Icon(Icons.Default.Delete, "Quitar ${linea.nombre}",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onMenos,
                    enabled = habilitado && linea.cantidad > 1) {
                    Icon(Icons.Default.Remove, "Quitar una unidad")
                }
                Text(linea.cantidad.toString(), Modifier.widthIn(min = 48.dp),
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                FilledTonalIconButton(onClick = onMas,
                    enabled = habilitado && linea.cantidad < MAX_POR_LINEA) {
                    Icon(Icons.Default.Add, "Agregar una unidad")
                }
            }
            Text(Dinero.clp(linea.subtotal), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FilaTotal(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = TintaSuave)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}
