package cl.efficientchile.inventario.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.efficientchile.inventario.data.Dinero
import cl.efficientchile.inventario.data.ItemVenta
import cl.efficientchile.inventario.data.LineaCarrito
import cl.efficientchile.inventario.data.Repo
import cl.efficientchile.inventario.data.VentaReq
import cl.efficientchile.inventario.data.VentaResp
import cl.efficientchile.inventario.util.Campanita
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_POR_LINEA = 999

@Composable
fun SaleScreen(
    repo: Repo,
    baseUrl: String,
    token: String,
    carrito: List<LineaCarrito>,
    comprobante: File?,
    /** El cliente decide en Ajustes si el numero de boleta es obligatorio. */
    exigirNumero: Boolean,
    /** "nunca" | "transferencia" | "siempre" */
    exigirFoto: String,
    onCambiarCantidad: (Int, Int) -> Unit,
    onQuitarLinea: (Int) -> Unit,
    onAgregarOtro: () -> Unit,
    onTomarComprobante: () -> Unit,
    onQuitarComprobante: () -> Unit,
    onConfirmado: () -> Unit,
    onCancelar: () -> Unit,
) {
    val fechaHora = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("es", "CL")).format(Date())
    }

    val total = carrito.sumOf { it.subtotal }
    val neto = Dinero.neto(total)
    val iva = Dinero.iva(total)
    val unidades = carrito.sumOf { it.cantidad }

    var tipoDoc by remember { mutableStateOf("boleta") }
    var formaPago by remember { mutableStateOf("efectivo") }
    var monto by remember { mutableStateOf("") }
    var numeroDoc by remember { mutableStateOf("") }

    // La foto es obligatoria segun lo que configuro el cliente. Cuando no lo
    // es, igual se ofrece el boton: hay ventas puntuales que conviene
    // respaldar sin que eso frene todas las demas.
    val fotoObligatoria = exigirFoto == "siempre" ||
            (exigirFoto == "transferencia" && formaPago == "transferencia")

    var rut by remember { mutableStateOf("") }
    var razon by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var giro by remember { mutableStateOf("") }

    var enviando by remember { mutableStateOf(false) }
    var estado by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf<VentaResp?>(null) }
    val scope = rememberCoroutineScope()

    // Diálogo de venta registrada, con el folio a la vista.
    exito?.let { v ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Venta registrada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        v.numeroTransaccion ?: "#${v.id}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Total: ${Dinero.clp(v.total ?: total)}")
                    if (!v.numeroDocumento.isNullOrBlank()) {
                        Text(
                            "Boleta ${v.numeroDocumento}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Neto ${Dinero.clp(v.neto ?: neto)} · IVA ${Dinero.clp(v.iva ?: iva)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (v.comprobante != null) {
                        Text(
                            "Comprobante archivado como ${v.comprobante}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { exito = null; onConfirmado() }) { Text("Listo") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Confirmar venta") }) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Fecha: $fechaHora", style = MaterialTheme.typography.bodyMedium)

            // ---------------------------------------------------- Carrito
            Text(
                "Productos ($unidades ${if (unidades == 1) "unidad" else "unidades"})",
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()
            if (carrito.isEmpty()) {
                Text(
                    "El carrito está vacío.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            carrito.forEachIndexed { i, linea ->
                LineaCarritoFila(
                    linea = linea,
                    habilitado = !enviando,
                    onMenos = { if (linea.cantidad > 1) onCambiarCantidad(i, linea.cantidad - 1) },
                    onMas = {
                        if (linea.cantidad < MAX_POR_LINEA) onCambiarCantidad(i, linea.cantidad + 1)
                    },
                    onQuitar = { onQuitarLinea(i) },
                )
                HorizontalDivider()
            }

            // ---------------------------------------------------- Totales
            FilaTotal("Neto", Dinero.clp(neto))
            FilaTotal("IVA (19%)", Dinero.clp(iva))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Total",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    Dinero.clp(total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider()

            // ------------------------------------------------ Documento
            Text("Tipo de documento", style = MaterialTheme.typography.titleMedium)
            Row {
                FilterChip(
                    selected = tipoDoc == "boleta",
                    onClick = { tipoDoc = "boleta" },
                    label = { Text("Boleta") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = tipoDoc == "factura",
                    onClick = { tipoDoc = "factura" },
                    label = { Text("Factura") },
                )
            }

            if (tipoDoc == "factura") {
                Text("Datos de la empresa", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(rut, { rut = it }, label = { Text("RUT empresa") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(razon, { razon = it }, label = { Text("Razón social") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(direccion, { direccion = it },
                    label = { Text("Dirección comercial") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(giro, { giro = it }, label = { Text("Giro") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "Estos datos quedan registrados para que el administrador emita la factura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // ------------------------------------------------ Forma de pago
            Text("Forma de pago", style = MaterialTheme.typography.titleMedium)
            Row {
                listOf("efectivo", "tarjeta", "transferencia").forEach { fp ->
                    FilterChip(
                        selected = formaPago == fp,
                        onClick = { formaPago = fp },
                        label = { Text(fp.replaceFirstChar { it.uppercase() }) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }

            if (formaPago == "efectivo") {
                OutlinedTextField(
                    monto,
                    { monto = it.filter { c -> c.isDigit() } },
                    label = { Text("Monto recibido (IVA incluido)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val recibido = monto.toDoubleOrNull() ?: 0.0
                if (recibido >= total && total > 0) {
                    Text(
                        "Vuelto: ${Dinero.clp(recibido - total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (monto.isNotBlank()) {
                    Text(
                        "Faltan ${Dinero.clp(total - recibido)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // --------------------------------------------- N de boleta
            OutlinedTextField(
                numeroDoc,
                { numeroDoc = it.take(60) },
                label = {
                    Text(
                        if (exigirNumero) "N° de boleta o comprobante"
                        else "N° de boleta o comprobante (opcional)"
                    )
                },
                supportingText = {
                    Text(
                        "Cópialo del papel que imprimió la máquina. Es lo que permite " +
                                "cuadrar después con el SII."
                    )
                },
                isError = exigirNumero && numeroDoc.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (exigirFoto != "nunca") {
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (comprobante == null) {
                            Text(
                                if (fotoObligatoria) "Falta la foto del comprobante"
                                else "Foto del comprobante (opcional)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (formaPago == "transferencia")
                                    "Fotografía la pantalla del cliente con la transferencia hecha. " +
                                            "Podrás revisarla antes de aceptarla."
                                else
                                    "Puedes adjuntar una foto del comprobante si esta venta " +
                                            "necesita respaldo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (fotoObligatoria) {
                                Button(
                                    onClick = onTomarComprobante,
                                    enabled = !enviando,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Tomar foto del comprobante") }
                            } else {
                                OutlinedButton(
                                    onClick = onTomarComprobante,
                                    enabled = !enviando,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Adjuntar foto") }
                            }
                        } else {
                            Text(
                                "Comprobante listo ✓",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Se subirá al servidor y quedará ligado al número de esta venta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onQuitarComprobante,
                                enabled = !enviando,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Repetir la foto") }
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (enviando && estado.isNotBlank()) {
                Text(
                    estado,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            OutlinedButton(
                onClick = onAgregarOtro,
                enabled = !enviando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Agregar otro producto (escanear más)") }

            Button(
                enabled = !enviando && carrito.isNotEmpty() && puedeConfirmar(
                    tipoDoc, formaPago, monto, total, rut, razon, direccion, giro,
                    comprobante, numeroDoc, exigirNumero, fotoObligatoria,
                ),
                onClick = {
                    enviando = true
                    error = null
                    scope.launch {
                        try {
                            // Si hay foto se sube, sea o no obligatoria para
                            // esta forma de pago.
                            val tokenComp = if (comprobante != null) {
                                estado = "Subiendo comprobante…"
                                repo.subirComprobante(baseUrl, token, comprobante).token
                            } else null

                            estado = "Registrando venta…"
                            val req = VentaReq(
                                items = carrito.map { ItemVenta(it.uids, it.cantidad) },
                                tipoDocumento = tipoDoc,
                                formaPago = formaPago,
                                montoPagado = if (formaPago == "efectivo")
                                    monto.toDoubleOrNull() else null,
                                numeroDocumento = numeroDoc.trim().ifBlank { null },
                                comprobanteToken = tokenComp,
                                rutEmpresa = if (tipoDoc == "factura") rut else null,
                                razonSocial = if (tipoDoc == "factura") razon else null,
                                direccionComercial = if (tipoDoc == "factura") direccion else null,
                                giro = if (tipoDoc == "factura") giro else null,
                            )
                            val resp = repo.crearVenta(baseUrl, token, req)
                            Campanita.sonar()
                            exito = resp
                        } catch (e: Exception) {
                            error = e.message ?: "Error al registrar la venta"
                        } finally {
                            enviando = false
                            estado = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (enviando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Confirmar venta")
                }
            }
            TextButton(
                onClick = onCancelar,
                enabled = !enviando,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancelar y volver") }
        }
    }
}

/** Una fila del carrito: producto, precio unitario, selector de cantidad y subtotal. */
@Composable
private fun LineaCarritoFila(
    linea: LineaCarrito,
    habilitado: Boolean,
    onMenos: () -> Unit,
    onMas: () -> Unit,
    onQuitar: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    linea.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "SKU ${linea.sku} · ${Dinero.clp(linea.precioVenta)} c/u",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onQuitar, enabled = habilitado) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Quitar ${linea.nombre}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onMenos,
                    enabled = habilitado && linea.cantidad > 1,
                ) { Icon(Icons.Default.Remove, contentDescription = "Quitar una unidad") }
                Text(
                    linea.cantidad.toString(),
                    Modifier.widthIn(min = 48.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                FilledTonalIconButton(
                    onClick = onMas,
                    enabled = habilitado && linea.cantidad < MAX_POR_LINEA,
                ) { Icon(Icons.Default.Add, contentDescription = "Agregar una unidad") }
            }
            Text(
                Dinero.clp(linea.subtotal),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FilaTotal(etiqueta: String, valor: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun puedeConfirmar(
    tipoDoc: String,
    formaPago: String,
    monto: String,
    total: Double,
    rut: String,
    razon: String,
    direccion: String,
    giro: String,
    comprobante: File?,
    numeroDoc: String,
    exigirNumero: Boolean,
    fotoObligatoria: Boolean,
): Boolean {
    if (formaPago == "efectivo") {
        val m = monto.toDoubleOrNull() ?: return false
        if (m < total) return false
    }
    if (exigirNumero && numeroDoc.isBlank()) return false
    if (fotoObligatoria && comprobante == null) return false
    if (tipoDoc == "factura") {
        if (rut.isBlank() || razon.isBlank() || direccion.isBlank() || giro.isBlank()) return false
    }
    return true
}
