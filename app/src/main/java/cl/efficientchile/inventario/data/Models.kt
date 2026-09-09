package cl.efficientchile.inventario.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Respuesta de /auth/login */
@JsonClass(generateAdapter = true)
data class TokenResp(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    val rol: String,
)

/** Producto público que devuelve /qr/especimen/{uid} al escanear */
@JsonClass(generateAdapter = true)
data class Especimen(
    val uid: String,
    val sku: String,
    val nombre: String,
    @Json(name = "precio_venta") val precioVenta: Double,
    val disponible: Boolean = true,
)

/** Un ítem del carrito: un UID escaneado + cantidad */
@JsonClass(generateAdapter = true)
data class ItemVenta(
    @Json(name = "uid_qr") val uidQr: String,
    val cantidad: Int = 1,
)

/** Payload para POST /ventas */
@JsonClass(generateAdapter = true)
data class VentaReq(
    val items: List<ItemVenta>,
    @Json(name = "tipo_documento") val tipoDocumento: String, // "boleta" | "factura"
    @Json(name = "forma_pago") val formaPago: String,          // "efectivo" | "tarjeta" | "transferencia"
    @Json(name = "monto_pagado") val montoPagado: Double? = null,
    @Json(name = "rut_empresa") val rutEmpresa: String? = null,
    @Json(name = "razon_social") val razonSocial: String? = null,
    @Json(name = "direccion_comercial") val direccionComercial: String? = null,
    val giro: String? = null,
)

/** Respuesta simplificada de /ventas */
@JsonClass(generateAdapter = true)
data class VentaResp(
    val id: Int,
    val total: Double? = null,
    @Json(name = "creado_en") val creadoEn: String? = null,
)

/** Error del backend: FastAPI usa {"detail": "..."} */
@JsonClass(generateAdapter = true)
data class ApiError(val detail: String)
