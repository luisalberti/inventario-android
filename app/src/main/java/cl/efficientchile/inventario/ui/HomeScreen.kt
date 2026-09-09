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
import cl.efficientchile.inventario.data.Especimen

@Composable
fun HomeScreen(
    username: String?,
    urlServer: String?,
    carrito: List<Especimen>,
    onEscanear: () -> Unit,
    onVer: () -> Unit,
    onVaciar: () -> Unit,
    onConfig: () -> Unit,
) {
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
                        text = "Empleado: ${username ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Servidor: ${urlServer ?: "-"}",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null,
                     modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text("Escanear QR", style = MaterialTheme.typography.headlineSmall)
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Carrito actual (${carrito.size})",
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
                    items(carrito) { e ->
                        ListItem(
                            headlineContent = { Text(e.nombre) },
                            supportingContent = { Text("SKU ${e.sku} · $${e.precioVenta.toInt()}") },
                            trailingContent = { Text("#${e.uid.take(6)}",
                                style = MaterialTheme.typography.bodySmall) },
                        )
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onVer, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuar con la venta")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
