package com.matedroid.di

import android.annotation.SuppressLint
import com.matedroid.data.api.NominatimApi
import com.matedroid.data.api.OpenMeteoApi
import com.matedroid.data.api.TeslamateApi
import com.matedroid.data.local.SettingsDataStore
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    val apiToken: String
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
     * @return A TeslamateApi instance configured for the given URL
     */
    fun create(baseUrl: String, acceptInvalidCerts: Boolean? = null): TeslamateApi {
        val normalizedUrl = baseUrl.trimEnd('/') + "/"
        val settings = runBlocking { settingsDataStore.settings.first() }
        val useInsecure = acceptInvalidCerts ?: settings.acceptInvalidCerts
        val apiToken = settings.apiToken

        val cacheKey = ApiCacheKey(normalizedUrl, useInsecure, apiToken)

        // Return cached API if available
        apiCache[cacheKey]?.let { return it }

        // Create new API instance
        val okHttpClient = createOkHttpClient(apiToken, useInsecure)

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

    private fun createOkHttpClient(apiToken: String, acceptInvalidCerts: Boolean): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = if (apiToken.isNotBlank()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $apiToken")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

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
