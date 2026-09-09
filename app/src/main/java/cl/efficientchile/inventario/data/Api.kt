package cl.efficientchile.inventario.data

import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface Api {
    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): TokenResp

    @GET("qr/especimen/{uid}")
    suspend fun leerEspecimen(
        @Header("Authorization") bearer: String,
        @Path("uid") uid: String,
    ): Especimen

    @POST("ventas")
    suspend fun crearVenta(
        @Header("Authorization") bearer: String,
        @Body venta: VentaReq,
    ): VentaResp
}
