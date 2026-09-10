package cl.efficientchile.inventario.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cl.efficientchile.inventario.data.Repo
import kotlinx.coroutines.launch

/** Pantalla inicial: URL del servidor + usuario + password.
 *  Guarda token en DataStore para no volver a pedirlo. */
@Composable
fun SetupScreen(repo: Repo, urlPrevia: String, onOk: () -> Unit) {
    var url by remember { mutableStateOf(urlPrevia) }
    var usuario by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Inventario", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Conecta este celular al servidor donde está la base de datos.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = url, onValueChange = { url = it.trim() },
            label = { Text("URL del servidor") },
            supportingText = { Text("Ej: https://scis1.powermedia.cl/") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = usuario, onValueChange = { usuario = it.trim() },
            label = { Text("Usuario") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible)
                            Icons.Filled.VisibilityOff
                        else
                            Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible)
                            "Ocultar contraseña"
                        else
                            "Mostrar contraseña"
                    )
                }
            }
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            enabled = !cargando && url.isNotBlank() && usuario.isNotBlank() && password.isNotBlank(),
            onClick = {
                error = null
                cargando = true
                scope.launch {
                    try {
                        val limpia = normalizarUrl(url)
                        url = limpia
                        repo.login(limpia, usuario, password)
                        onOk()
                    } catch (e: Exception) {
                        error = e.message ?: "Error al conectar"
                    } finally {
                        cargando = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (cargando) CircularProgressIndicator(
                modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            ) else Text("Entrar")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Escribe solo el dominio si quieres: la app le agrega https:// y la " +
                    "barra final. Se guarda una sesión y no vuelve a pedirte " +
                    "contraseña hasta que la cierres.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Deja la URL en la forma que el backend necesita.
 *
 * Un dominio escrito con http:// hace que Hostinger responda 301 hacia
 * https, y ese redirect convierte el POST del login en un GET: el servidor
 * contesta "Solo POST" y parece que la clave está mala. Por eso se fuerza
 * https salvo cuando apunta a una IP local, donde sí puede no haber
 * certificado.
 */
internal fun normalizarUrl(entrada: String): String {
    var u = entrada.trim()
    if (u.isEmpty()) return u
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
    if (u.startsWith("http://")) {
        val host = u.removePrefix("http://").substringBefore("/").substringBefore(":")
        val esLocal = host == "localhost" ||
                host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))
        if (!esLocal) u = "https://" + u.removePrefix("http://")
    }
    if (!u.endsWith("/")) u += "/"
    return u
}
