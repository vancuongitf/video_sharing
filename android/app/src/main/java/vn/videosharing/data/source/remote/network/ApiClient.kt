package vn.videosharing.data.source.remote.network

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.videosharing.BuildConfig
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/**
 *
 * @author cuongcao.
 */

open class ApiClient private constructor(url: String? = null) {

    internal var token: String? = null
    internal var language: String? = null
    internal var region: String? = null
    internal var isFromUnitTest: Boolean = false
    private var baseUrl: String =
        if (url == null || url.isEmpty()) BuildConfig.BASE_API_URL else url

    companion object : SingletonHolder<ApiClient, String>(::ApiClient) {
        private const val API_TIMEOUT = 10L // 10 minutes
        private const val API_READ_TIMEOUT = 1L
    }

    val service: ApiService
        get() {
            return createService()
        }

    private fun createService(): ApiService {
        val httpClientBuilder = OkHttpClient.Builder()
        httpClientBuilder.interceptors().add(Interceptor { chain ->
            val original = chain.request()
            // Request customization: add request headers
            val requestBuilder = original.newBuilder()
                .method(original.method(), original.body())
            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            requestBuilder.addHeader("accept-language", language ?: "en")

            region?.let {
                requestBuilder.addHeader("Accept-Region", it)
            }
            val request = requestBuilder.build()
            chain.proceed(request)
        })

        val client = httpClientBuilder
            .connectTimeout(API_TIMEOUT, TimeUnit.MINUTES)
            .writeTimeout(API_TIMEOUT, TimeUnit.MINUTES)
            .readTimeout(API_READ_TIMEOUT, TimeUnit.MINUTES)
            .build()
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .serializeNulls()
            .create()

        val nullOnEmptyConverterFactory = object : Converter.Factory() {
            fun converterFactory() = this
            override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit) =
                object : Converter<ResponseBody, Any?> {
                    val nextResponseBodyConverter =
                        retrofit.nextResponseBodyConverter<Any?>(converterFactory(), type, annotations)

                    override fun convert(value: ResponseBody) =
                        if (value.contentLength() != 0L) nextResponseBodyConverter.convert(value) else null
                }
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(nullOnEmptyConverterFactory)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(CustomCallAdapterFactory.create())
            .client(client)
            .build()
        return retrofit.create(ApiService::class.java)
    }
}

/**
 * Use this class to create singleton object with argument
 */
open class SingletonHolder<out T, in A>(private var creator: (A?) -> T) {
    @kotlin.jvm.Volatile
    private var instance: T? = null

    /**
     * Generate instance for T class with argument A
     */
    fun getInstance(arg: A?, isFromUnitTest: Boolean = false): T {
        val i = instance
        if (i != null) {
            return i
        }

        return synchronized(this) {
            val i2 = instance
            if (i2 != null) {
                i2
            } else {
                val created = creator(arg)
                if (isFromUnitTest) {
                    (created as? ApiClient)?.isFromUnitTest = isFromUnitTest
                }
                instance = created
                created
            }
        }
    }

    /**
     * Clear current instance
     */
    fun clearInstance() {
        instance = null
    }
}
