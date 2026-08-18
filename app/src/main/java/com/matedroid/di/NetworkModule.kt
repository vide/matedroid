package com.matedroid.di

import android.annotation.SuppressLint
import com.matedroid.BuildConfig
import com.matedroid.data.api.NominatimApi
import com.matedroid.data.api.OpenMeteoApi
import com.matedroid.data.api.TeslamateApi
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.domain.ConnectionTimeout
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val USER_AGENT = "MateDroid/${BuildConfig.VERSION_NAME}"

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .build()
    }

    @Provides
    @Singleton
    fun provideTeslamateApiFactory(
        settingsDataStore: SettingsDataStore,
        moshi: Moshi
    ): TeslamateApiFactory {
        return TeslamateApiFactory(settingsDataStore, moshi)
    }

    @Provides
    @Singleton
    fun provideNominatimApi(moshi: Moshi): NominatimApi {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NominatimApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenMeteoApi(moshi: Moshi): OpenMeteoApi {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://archive-api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoApi::class.java)
    }
}

/**
 * Cache key for API instances, combining URL and security settings.
 */
private data class ApiCacheKey(
    val baseUrl: String,
    val acceptInvalidCerts: Boolean,
    val apiToken: String,
    val httpBasicAuthUsername: String,
    val httpBasicAuthPassword: String,
    val connectTimeoutSeconds: Int
)

/**
 * Factory for creating TeslamateApi instances with caching support.
 *
 * Supports caching multiple API instances (e.g., for primary and secondary servers)
 * to avoid recreating clients when switching between servers during fallback.
 */
class TeslamateApiFactory(
    private val settingsDataStore: SettingsDataStore,
    private val moshi: Moshi
) {
    // Cache multiple API instances keyed by their configuration
    private val apiCache = mutableMapOf<ApiCacheKey, TeslamateApi>()

    /**
     * Creates or returns a cached TeslamateApi instance for the given URL.
     *
     * @param baseUrl The base URL for the API
     * @param acceptInvalidCerts Override for accepting invalid certificates. If null, uses the setting from DataStore.
     * @param connectTimeoutSeconds Override for the connect timeout, already resolved. If null,
     *   it is resolved from the settings in DataStore — see [ConnectionTimeout].
     * @return A TeslamateApi instance configured for the given URL
     */
    suspend fun create(
        baseUrl: String,
        acceptInvalidCerts: Boolean? = null,
        connectTimeoutSeconds: Int? = null
    ): TeslamateApi {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        val settings = settingsDataStore.settings.first()
        val useInsecure = acceptInvalidCerts ?: settings.acceptInvalidCerts
        val apiToken = settings.apiToken
        val basicAuthUsername = settings.httpBasicAuthUsername
        val basicAuthPassword = settings.httpBasicAuthPassword
        val timeoutSeconds = connectTimeoutSeconds ?: ConnectionTimeout.resolveSeconds(
            setting = settings.connectTimeoutSeconds,
            hasFallbackServer = settings.hasSecondaryServer
        )

        // The timeout is part of the key so changing it in Settings takes effect on the next
        // request rather than on the next app start.
        val cacheKey = ApiCacheKey(
            normalizedUrl,
            useInsecure,
            apiToken,
            basicAuthUsername,
            basicAuthPassword,
            timeoutSeconds
        )

        // Return cached API if available
        apiCache[cacheKey]?.let { return it }

        // Create new API instance
        val okHttpClient = createOkHttpClient(
            apiToken,
            useInsecure,
            basicAuthUsername,
            basicAuthPassword,
            timeoutSeconds
        )

        val api = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TeslamateApi::class.java)

        // Cache the API instance
        apiCache[cacheKey] = api

        // Limit cache size to prevent memory leaks (keep last 4 configurations)
        if (apiCache.size > 4) {
            val oldestKey = apiCache.keys.first()
            apiCache.remove(oldestKey)
        }

        return api
    }

    /**
     * Invalidates all cached API instances.
     * Call this when settings change that require recreating the API clients.
     */
    fun invalidateCache() {
        apiCache.clear()
    }

    /**
     * Internal rather than private so a unit test can assert that the configured timeout
     * really reaches OkHttp, in the unit OkHttp expects.
     */
    internal fun createOkHttpClient(
        apiToken: String,
        acceptInvalidCerts: Boolean,
        basicAuthUsername: String = "",
        basicAuthPassword: String = "",
        connectTimeoutSeconds: Int = ConnectionTimeout.WITHOUT_FALLBACK_SECONDS
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .header("User-Agent", "MateDroid/${BuildConfig.VERSION_NAME}")
                // A request must carry a single Authorization header — addHeader() appends,
                // and duplicate Authorization headers get rejected/mishandled by many
                // proxies and servers. When both credentials are configured the API token
                // wins, as it's the credential TeslamateApi itself validates.
                if (apiToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiToken")
                } else if (basicAuthUsername.isNotBlank() && basicAuthPassword.isNotBlank()) {
                    requestBuilder.header("Authorization",
                        okhttp3.Credentials.basic(basicAuthUsername, basicAuthPassword))
                }
                chain.proceed(requestBuilder.build())
            }
            // User-configurable, and short by default when a fallback server is configured:
            // executeWithFallback tries the primary server on EVERY request, so dual-address
            // setups (local IP + VPN IP) hit this timeout on each call while on the other
            // network before falling back. See ConnectionTimeout for the whole trade-off.
            .connectTimeout(connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Only add logging in debug builds, and use HEADERS level to avoid OOM
        // with large response bodies (drive details can be 15MB+)
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
                // Keep credentials (Bearer token / Basic auth) out of logcat
                redactHeader("Authorization")
            }
            builder.addInterceptor(loggingInterceptor)
        }

        if (acceptInvalidCerts) {
            configureInsecureTls(builder)
        }

        return builder.build()
    }

    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private fun configureInsecureTls(builder: OkHttpClient.Builder) {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        builder.hostnameVerifier { _, _ -> true }
    }
}
