package cl.efficientchile.inventario.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Blanco, cyan y azul marino.
 *
 * Una decision que conviene entender antes de cambiarla: **el cyan no se usa
 * para botones con texto blanco encima**. #00A8CC con blanco da 2,9 a 1 de
 * contraste, y un mesón de vivero a pleno sol es el peor lugar para leer
 * texto de bajo contraste. El boton principal va azul marino, que da 14 a 1.
 *
 * El cyan es acento: la barra de avance, el paso en que vas, el chip elegido.
 *
 * Y va **sobre el azul marino de la cabecera**, no sobre blanco. Medido:
 * cyan sobre blanco da 2,8 a 1 y ni siquiera llega al minimo de 3 a 1 que se
 * le pide a algo que no es texto, asi que una barra cyan sobre fondo blanco
 * desaparece. Sobre el marino da 5,5 a 1 y se ve de lejos. Donde el cyan
 * tiene que tocar blanco —el borde de un campo activo— se usa CyanProfundo.
 */

val Marino     = Color(0xFF0A2540)   // barras, texto, boton principal
val MarinoSuave= Color(0xFF16385C)   // presionado
val Cyan       = Color(0xFF00A8CC)   // acento
val CyanProfundo = Color(0xFF00779B) // cyan que SI se puede leer sobre blanco
val CyanPalido = Color(0xFFE3F5FA)   // fondo de chip elegido
val Blanco     = Color(0xFFFFFFFF)
val Nieve      = Color(0xFFF5F9FC)   // fondo de pantalla
val Borde      = Color(0xFFC9D6E2)
val Tinta      = Color(0xFF0A2540)
val TintaSuave = Color(0xFF5A6B7D)
val Alerta     = Color(0xFFB3261E)
val Bien       = Color(0xFF0F7B4F)

private val claro = lightColorScheme(
    primary = Marino,
    onPrimary = Blanco,
    primaryContainer = CyanPalido,
    onPrimaryContainer = Marino,
    secondary = CyanProfundo,
    onSecondary = Blanco,
    secondaryContainer = CyanPalido,
    onSecondaryContainer = Marino,
    tertiary = Cyan,
    background = Nieve,
    onBackground = Tinta,
    surface = Blanco,
    onSurface = Tinta,
    surfaceVariant = Nieve,
    onSurfaceVariant = TintaSuave,
    outline = Borde,
    error = Alerta,
    onError = Blanco,
)

/* El vendedor usa esto de dia, en un mesón, muchas veces con el telefono en
   una mano. El modo oscuro no le sirve de nada y duplica las combinaciones
   que hay que revisar, asi que la app se queda en claro a proposito. */
private val oscuro = claro

private val tipos = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun TemaInventario(contenido: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = claro, typography = tipos, content = contenido)
}
