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
import cl.efficientchile.inventario.data.Especimen
import cl.efficientchile.inventario.ui.HomeScreen
import cl.efficientchile.inventario.ui.SaleScreen
import cl.efficientchile.inventario.ui.ScannerScreen
import cl.efficientchile.inventario.ui.SetupScreen

sealed class Screen {
    data object Home : Screen()
    data object Scanner : Screen()
    data object Sale : Screen()
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

    // Carrito compartido entre Scanner y Sale (en memoria — se limpia al confirmar)
    val carrito = remember { mutableStateListOf<Especimen>() }
    var pantalla by remember { mutableStateOf<Screen>(Screen.Home) }

    // Decidir si hay que ir a Setup (sin token) o Home
    LaunchedEffect(token) {
        if (token.isNullOrBlank() && pantalla !is Screen.Setup) {
            pantalla = Screen.Setup
        }
    }

    when (pantalla) {
        Screen.Setup -> SetupScreen(
            repo = repo,
            urlPrevia = baseUrl ?: "http://192.168.1.10:8000",
            onOk = { pantalla = Screen.Home },
        )
        Screen.Home -> HomeScreen(
            username = username,
            urlServer = baseUrl,
            carrito = carrito,
            onEscanear = { pantalla = Screen.Scanner },
            onVer = { pantalla = Screen.Sale },
            onVaciar = { carrito.clear() },
            onConfig = { pantalla = Screen.Setup },
        )
        Screen.Scanner -> ScannerScreen(
            repo = repo,
            baseUrl = baseUrl.orEmpty(),
            token = token.orEmpty(),
            uidsYaEnCarrito = carrito.map { it.uid }.toSet(),
            onEscaneado = { esp ->
                carrito.add(esp)
                pantalla = Screen.Sale
            },
            onCerrar = { pantalla = Screen.Home },
        )
        Screen.Sale -> SaleScreen(
            repo = repo,
            baseUrl = baseUrl.orEmpty(),
            token = token.orEmpty(),
            carrito = carrito,
            onAgregarOtro = { pantalla = Screen.Scanner },
            onConfirmado = {
                carrito.clear()
                pantalla = Screen.Home
            },
            onCancelar = { pantalla = Screen.Home },
        )
    }
}
