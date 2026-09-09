package cl.efficientchile.inventario.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.efficientchile.inventario.data.Especimen
import cl.efficientchile.inventario.data.ItemVenta
import cl.efficientchile.inventario.data.Repo
import cl.efficientchile.inventario.data.VentaReq
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formulario de venta. Fecha y hora se muestran precargadas (el backend
 *  usa su reloj, pero se muestra para dar contexto al vendedor). */
@Composable
fun SaleScreen(
    repo: Repo,
    baseUrl: String,
    token: String,
    carrito: List<Especimen>,
    onAgregarOtro: () -> Unit,
    onConfirmado: () -> Unit,
    onCancelar: () -> Unit,
) {
    val fechaHora = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("es", "CL")).format(Date())
    }
    val total = carrito.sumOf { it.precioVenta }

    var tipoDoc by remember { mutableStateOf("boleta") }   // boleta | factura
    var formaPago by remember { mutableStateOf("efectivo") } // efectivo | tarjeta | transferencia
    var monto by remember { mutableStateOf("") }

    var rut by remember { mutableStateOf("") }
    var razon by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var giro by remember { mutableStateOf("") }

    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            Text("Ítems: ${carrito.size}", style = MaterialTheme.typography.bodyMedium)
            Text("Total: $${total.toInt()}",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider()

            Text("Tipo de documento", style = MaterialTheme.typography.titleMedium)
            Row {
                FilterChip(selected = tipoDoc == "boleta",
                    onClick = { tipoDoc = "boleta" }, label = { Text("Boleta") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = tipoDoc == "factura",
                    onClick = { tipoDoc = "factura" }, label = { Text("Factura") })
            }

            if (tipoDoc == "factura") {
                Text("Datos de la empresa", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(rut, { rut = it }, label = { Text("RUT empresa") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(razon, { razon = it }, label = { Text("Razón social") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(direccion, { direccion = it },
                    label = { Text("Dirección comercial") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(giro, { giro = it }, label = { Text("Giro") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "Se enviará un correo al administrador con estos datos para " +
                            "emitir la factura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()
            Text("Forma de pago", style = MaterialTheme.typography.titleMedium)
            Row {
                listOf("efectivo", "tarjeta", "transferencia").forEach { fp ->
                    FilterChip(selected = formaPago == fp, onClick = { formaPago = fp },
                        label = { Text(fp.replaceFirstChar { it.uppercase() }) })
                    Spacer(Modifier.width(8.dp))
                }
            }

            if (formaPago == "efectivo") {
                OutlinedTextField(monto, { monto = it.filter { c -> c.isDigit() } },
                    label = { Text("Monto recibido (IVA incluido)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                val vuelto = (monto.toDoubleOrNull() ?: 0.0) - total
                if (vuelto > 0) {
                    Text("Vuelto: $${vuelto.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            OutlinedButton(onClick = onAgregarOtro, modifier = Modifier.fillMaxWidth()) {
                Text("Agregar otro producto (escanear más)")
            }
            Button(
                enabled = !enviando && carrito.isNotEmpty() && puedeConfirmar(
                    tipoDoc, formaPago, monto, total, rut, razon, direccion, giro
                ),
                onClick = {
                    enviando = true; error = null
                    scope.launch {
                        try {
                            val req = VentaReq(
                                items = carrito.map { ItemVenta(it.uid, 1) },
                                tipoDocumento = tipoDoc,
                                formaPago = formaPago,
                                montoPagado = if (formaPago == "efectivo")
                                    monto.toDoubleOrNull() else null,
                                rutEmpresa = if (tipoDoc == "factura") rut else null,
                                razonSocial = if (tipoDoc == "factura") razon else null,
                                direccionComercial = if (tipoDoc == "factura") direccion else null,
                                giro = if (tipoDoc == "factura") giro else null,
                            )
                            repo.crearVenta(baseUrl, token, req)
                            onConfirmado()
                        } catch (e: Exception) {
                            error = e.message ?: "Error al enviar la venta"
                        } finally {
                            enviando = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (enviando) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                ) else Text("Confirmar venta")
            }
            TextButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar y volver")
            }
        }
    }
}

private fun puedeConfirmar(
    tipoDoc: String, formaPago: String, monto: String, total: Double,
    rut: String, razon: String, direccion: String, giro: String,
): Boolean {
    if (formaPago == "efectivo") {
        val m = monto.toDoubleOrNull() ?: return false
        if (m < total) return false
    }
    if (tipoDoc == "factura") {
        if (rut.isBlank() || razon.isBlank() || direccion.isBlank() || giro.isBlank()) return false
    }
    return true
}
