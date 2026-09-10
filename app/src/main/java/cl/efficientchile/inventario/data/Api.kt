package cl.efficientchile.inventario.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

// Rutas de la API PHP alojada en Hostinger.
// La base URL en Setup va con https y barra final:
//   https://scis1.powermedia.cl/

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

    /** Sube la foto del comprobante de transferencia antes de registrar la venta. */
    @Multipart
    @POST("comprobante.php")
    suspend fun subirComprobante(
        @Header("Authorization") bearer: String,
        @Part foto: MultipartBody.Part,
    ): ComprobanteResp
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class LoginReq(
    val username: String,
    val password: String,
    @com.squareup.moshi.Json(name = "tenant_id") val tenantId: Int = 1,
)
