package cl.efficientchile.inventario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.efficientchile.inventario.data.LineaCarrito
import cl.efficientchile.inventario.ui.ComprobanteScreen
import cl.efficientchile.inventario.ui.HomeScreen
import cl.efficientchile.inventario.ui.SaleScreen
import cl.efficientchile.inventario.ui.ScannerScreen
import cl.efficientchile.inventario.ui.SetupScreen
import java.io.File

sealed class Screen {
    data object Home : Screen()
    data object Scanner : Screen()
    data object Sale : Screen()
    data object Comprobante : Screen()
    data object Setup : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current.applicationContext as App
    val prefs = ctx.prefs
    val repo = ctx.repo

    val token by prefs.token.collectAsStateWithLifecycle(initialValue = null)
    val baseUrl by prefs.baseUrl.collectAsStateWithLifecycle(initialValue = null)
    val username by prefs.username.collectAsStateWithLifecycle(initialValue = null)
    // Que pedirle al vendedor: lo define el cliente en el panel y llega en el login.
    val exigirNumero by prefs.exigirNumero.collectAsStateWithLifecycle(initialValue = true)
    val exigirFoto by prefs.exigirFoto.collectAsStateWithLifecycle(initialValue = "transferencia")

    // Carrito en memoria: una linea por SKU, con los UID escaneados dentro.
    val carrito = remember { mutableStateListOf<LineaCarrito>() }
    // Foto del comprobante de transferencia, si la forma de pago la exige.
    var comprobante by remember { mutableStateOf<File?>(null) }
    var pantalla by remember { mutableStateOf<Screen>(Screen.Home) }

    fun limpiarComprobante() {
        comprobante?.delete()
        comprobante = null
    }

    LaunchedEffect(token) {
        if (token.isNullOrBlank() && pantalla !is Screen.Setup) pantalla = Screen.Setup
    }

    when (pantalla) {
        Screen.Setup -> SetupScreen(
            repo = repo,
            urlPrevia = baseUrl ?: "https://scis1.powermedia.cl/",
            onOk = { pantalla = Screen.Home },
        )

        Screen.Home -> HomeScreen(
            username = username,
            urlServer = baseUrl,
            carrito = carrito,
            onEscanear = { pantalla = Screen.Scanner },
            onVer = { pantalla = Screen.Sale },
            onVaciar = { carrito.clear(); limpiarComprobante() },
            onConfig = { pantalla = Screen.Setup },
        )

        Screen.Scanner -> ScannerScreen(
            repo = repo,
            baseUrl = baseUrl.orEmpty(),
            token = token.orEmpty(),
            uidsYaEnCarrito = carrito.flatMap { it.uids }.toSet(),
            onEscaneado = { esp ->
                // Mismo SKU -> se suma a la linea existente en vez de duplicarla.
                val i = carrito.indexOfFirst { it.sku == esp.sku }
                if (i >= 0) {
                    val l = carrito[i]
                    if (esp.uid !in l.uids) {
                        carrito[i] = l.copy(
                            uids = l.uids + esp.uid,
                            cantidad = l.cantidad + 1,
                        )
                    }
                } else {
                    carrito.add(
                        LineaCarrito(
                            sku = esp.sku,
                            nombre = esp.nombre,
                            precioVenta = esp.precioVenta,
                            uids = listOf(esp.uid),
                            cantidad = 1,
                        )
                    )
                }
                pantalla = Screen.Sale
            },
            onCerrar = { pantalla = Screen.Home },
        )

        Screen.Sale -> SaleScreen(
            repo = repo,
            baseUrl = baseUrl.orEmpty(),
            token = token.orEmpty(),
            carrito = carrito,
            comprobante = comprobante,
            exigirNumero = exigirNumero,
            exigirFoto = exigirFoto,
            onCambiarCantidad = { i, nueva ->
                if (i in carrito.indices && nueva >= 1) {
                    val l = carrito[i]
                    // Si baja la cantidad por debajo de los QR escaneados, se
                    // sueltan los ultimos: el servidor no debe marcarlos vendidos.
                    val uids = if (nueva < l.uids.size) l.uids.take(nueva) else l.uids
                    carrito[i] = l.copy(uids = uids, cantidad = nueva)
                }
            },
            onQuitarLinea = { i -> if (i in carrito.indices) carrito.removeAt(i) },
            onAgregarOtro = { pantalla = Screen.Scanner },
            onTomarComprobante = { pantalla = Screen.Comprobante },
            onQuitarComprobante = {
                limpiarComprobante()
                pantalla = Screen.Comprobante
            },
            onConfirmado = {
                carrito.clear()
                limpiarComprobante()
                pantalla = Screen.Home
            },
            onCancelar = { pantalla = Screen.Home },
        )

        Screen.Comprobante -> ComprobanteScreen(
            onListo = { archivo ->
                comprobante?.delete()
                comprobante = archivo
                pantalla = Screen.Sale
            },
            onCancelar = { pantalla = Screen.Sale },
        )
    }
}
