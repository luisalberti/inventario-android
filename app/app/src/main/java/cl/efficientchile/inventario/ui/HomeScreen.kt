package cl.efficientchile.inventario.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.efficientchile.inventario.data.Dinero
import cl.efficientchile.inventario.data.LineaCarrito

@Composable
fun HomeScreen(
    username: String?,
    urlServer: String?,
    carrito: List<LineaCarrito>,
    onEscanear: () -> Unit,
    onVer: () -> Unit,
    onVaciar: () -> Unit,
    onConfig: () -> Unit,
) {
    val unidades = carrito.sumOf { it.cantidad }
    val total = carrito.sumOf { it.subtotal }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventario") },
                actions = {
                    IconButton(onClick = onConfig) {
                        Icon(Icons.Default.Settings, contentDescription = "Config")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Empleado: ${username ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Servidor: ${urlServer ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onEscanear,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("Escanear QR", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Carrito ($unidades ${if (unidades == 1) "unidad" else "unidades"})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (carrito.isNotEmpty()) {
                    TextButton(onClick = onVaciar) { Text("Vaciar") }
                }
            }
            HorizontalDivider()

            if (carrito.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "Ningún producto escaneado. Toca “Escanear QR”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(carrito) { l ->
                        ListItem(
                            headlineContent = { Text(l.nombre) },
                            supportingContent = {
                                Text("SKU ${l.sku} · ${Dinero.clp(l.precioVenta)} c/u")
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "x${l.cantidad}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        Dinero.clp(l.subtotal),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        Dinero.clp(total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(onClick = onVer, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuar con la venta")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
