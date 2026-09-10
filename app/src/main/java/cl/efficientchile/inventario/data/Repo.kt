package cl.efficientchile.inventario.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class Repo(private val prefs: Prefs) {

    private var cachedApi: Pair<String, Api>? = null

    private fun api(baseUrl: String): Api {
        val normalizado = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        cachedApi?.let { if (it.first == normalizado) return it.second }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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
        return detail ?: "HTTP ${e.code()}: ${e.message()}"
    }

    suspend fun login(url: String, user: String, password: String, tenantId: Int = 1) {
        try {
            val t = api(url).login(LoginReq(user, password, tenantId))
            prefs.guardarLogin(url, t.accessToken, user)
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
}
