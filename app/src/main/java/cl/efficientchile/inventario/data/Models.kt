package cl.efficientchile.inventario.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

/** Respuesta de auth.php */
@JsonClass(generateAdapter = true)
data class TokenResp(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    val rol: String,
    val empresa: String? = null,
    val licencia: String? = null,
    @Json(name = "licencia_aviso") val licenciaAviso: String? = null,
    // Que pedirle al vendedor. Lo configura el cliente en el panel; la app
    // arma la pantalla de venta con esto y no vuelve a preguntar.
    @Json(name = "exigir_numero") val exigirNumero: Boolean = true,
    @Json(name = "exigir_foto") val exigirFoto: String = "transferencia",
)

/** Datos publicos del QR escaneado (qr.php?accion=especimen) */
@JsonClass(generateAdapter = true)
data class Especimen(
    val uid: String,
    val sku: String,
    val nombre: String,
    @Json(name = "precio_venta") val precioVenta: Double,
    val disponible: Boolean = true,
)

/**
 * Una linea del carrito: todas las unidades de un mismo SKU.
 *
 * `uids` son los QR realmente escaneados. `cantidad` puede ser mayor si el
 * trabajador la sube a mano desde el carrito; en ese caso el servidor toma
 * los especimenes que falten del mismo producto, para que el inventario
 * no se descuadre.
 */
data class LineaCarrito(
    val sku: String,
    val nombre: String,
    val precioVenta: Double,
    val uids: List<String>,
    val cantidad: Int = uids.size,
) {
    val subtotal: Double get() = precioVenta * cantidad
}

/** Un item tal como lo espera venta.php */
@JsonClass(generateAdapter = true)
data class ItemVenta(
    @Json(name = "uids_qr") val uidsQr: List<String>,
    val cantidad: Int,
)

/** Payload de venta.php */
@JsonClass(generateAdapter = true)
data class VentaReq(
    val items: List<ItemVenta>,
    @Json(name = "tipo_documento") val tipoDocumento: String,   // boleta | factura
    @Json(name = "forma_pago") val formaPago: String,           // efectivo | tarjeta | transferencia
    @Json(name = "monto_pagado") val montoPagado: Double? = null,
    @Json(name = "numero_documento") val numeroDocumento: String? = null,
    @Json(name = "comprobante_token") val comprobanteToken: String? = null,
    @Json(name = "banco_operacion") val bancoOperacion: String? = null,
    @Json(name = "rut_empresa") val rutEmpresa: String? = null,
    @Json(name = "razon_social") val razonSocial: String? = null,
    @Json(name = "direccion_comercial") val direccionComercial: String? = null,
    val giro: String? = null,
)

/** Respuesta de venta.php */
@JsonClass(generateAdapter = true)
data class VentaResp(
    val id: Int,
    @Json(name = "numero_transaccion") val numeroTransaccion: String? = null,
    @Json(name = "numero_documento") val numeroDocumento: String? = null,
    val total: Double? = null,
    val neto: Double? = null,
    val iva: Double? = null,
    val comprobante: String? = null,
    @Json(name = "creado_en") val creadoEn: String? = null,
)

/** Respuesta de comprobante.php al subir la foto */
@JsonClass(generateAdapter = true)
data class ComprobanteResp(
    val token: String,
    val bytes: Int = 0,
    val ancho: Int = 0,
    val alto: Int = 0,
)

/** Error del backend: {"detail": "..."} */
@JsonClass(generateAdapter = true)
data class ApiError(val detail: String)

// ---------------------------------------------------------------------
//  Dinero e IVA
// ---------------------------------------------------------------------

/**
 * Los precios cargados en la base YA INCLUYEN IVA (convencion retail
 * chilena). El neto se saca dividiendo por 1,19 y el IVA es la diferencia.
 * Se calcula igual que en venta.php para que la pantalla y el servidor
 * muestren siempre lo mismo.
 */
object Dinero {
    const val TASA_IVA = 0.19

    fun neto(total: Double): Double = redondear2(total / (1 + TASA_IVA))

    fun iva(total: Double): Double = redondear2(total - neto(total))

    private fun redondear2(v: Double): Double = (v * 100.0).roundToLong() / 100.0

    private val formato = DecimalFormat(
        "#,##0",
        DecimalFormatSymbols(Locale.US).apply { groupingSeparator = '.' },
    )

    /** 12345.0 -> "$12.345" */
    fun clp(monto: Double): String = "$" + formato.format(monto.roundToLong())
}
