package cl.efficientchile.inventario.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// Rutas adaptadas para la API PHP alojada en Hostinger.
// La base URL en Setup debe ser: https://scis1.powermedia.cl/

interface Api {
    @POST("auth.php")
    suspend fun login(@Body body: LoginReq): TokenResp

    @GET("qr.php")
    suspend fun leerEspecimen(
        @Header("Authorization") bearer: String,
        @Query("accion") accion: String = "especimen",
        @Query("uid") uid: String,
    ): Especimen

    @POST("venta.php")
    suspend fun crearVenta(
        @Header("Authorization") bearer: String,
        @Body venta: VentaReq,
    ): VentaResp
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class LoginReq(
    val username: String,
    val password: String,
    @com.squareup.moshi.Json(name = "tenant_id") val tenantId: Int = 1,
)
