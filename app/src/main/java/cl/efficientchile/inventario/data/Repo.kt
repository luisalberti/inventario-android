package cl.efficientchile.inventario.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class Repo(private val prefs: Prefs) {

    private var cachedApi: Pair<String, Api>? = null

    private fun api(baseUrl: String): Api {
        val normalizado = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        cachedApi?.let { if (it.first == normalizado) return it.second }
        val client = OkHttpClient.Builder()
            // Hostinger responde 301 a todo lo que llegue por http. Si OkHttp
            // sigue ese redirect convierte el POST en GET y el backend
            // contesta "Solo POST". Con esto el 301 se ve tal cual y queda
            // claro que falta la "s" de https en la URL.
            .protocols(listOf(Protocol.HTTP_1_1))
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "InventarioApp/2.0")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val nueva = Retrofit.Builder()
            .baseUrl(normalizado)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Api::class.java)
        cachedApi = normalizado to nueva
        return nueva
    }

    private fun httpMsg(e: HttpException): String {
        val body = try { e.response()?.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(ApiError::class.java)
        val detail = try { adapter.fromJson(body)?.detail } catch (_: Exception) { null }
        if (detail != null) return detail
        return when (e.code()) {
            301, 302, 307, 308 ->
                "El servidor redirige la conexion. Revisa que la URL empiece con https://"
            else -> "HTTP ${e.code()}: ${e.message()}"
        }
    }

    /**
     * Entra y devuelve el aviso que mande el servidor, o cadena vacia.
     *
     * El rechazo por permiso cortado o por pago vencido no llega por aca: el
     * servidor responde 403 o 402 y httpMsg() ya saca el motivo del cuerpo,
     * asi que el vendedor ve la frase que escribio el proveedor y no un
     * codigo. Lo que se devuelve aca es el aviso de que TODAVIA funciona pero
     * esta por vencer, que conviene mostrar sin bloquear nada.
     */
    suspend fun login(url: String, user: String, password: String, tenantId: Int = 1): String {
        try {
            val t = api(url).login(LoginReq(user, password, tenantId))
            prefs.guardarLogin(url, t.accessToken, user)
            return if (t.licencia == "por_vencer" || t.licencia == "gracia") t.licenciaAviso else ""
        } catch (e: HttpException) {
            throw RuntimeException(httpMsg(e))
        }
    }

    suspend fun leerEspecimen(url: String, token: String, uid: String): Especimen {
        try {
            return api(url).leerEspecimen("Bearer $token", uid = uid)
        } catch (e: HttpException) {
            throw RuntimeException(httpMsg(e))
        }
    }

    suspend fun crearVenta(url: String, token: String, venta: VentaReq): VentaResp {
        try {
            return api(url).crearVenta("Bearer $token", venta)
        } catch (e: HttpException) {
            throw RuntimeException(httpMsg(e))
        }
    }

    /** Sube la foto del comprobante y devuelve el token con que se liga a la venta. */
    suspend fun subirComprobante(url: String, token: String, archivo: File): ComprobanteResp {
        try {
            val tipo = if (archivo.extension.lowercase() == "png") "image/png" else "image/jpeg"
            val cuerpo = archivo.asRequestBody(tipo.toMediaType())
            val parte = MultipartBody.Part.createFormData("foto", archivo.name, cuerpo)
            return api(url).subirComprobante("Bearer $token", parte)
        } catch (e: HttpException) {
            throw RuntimeException(httpMsg(e))
        }
    }
}
