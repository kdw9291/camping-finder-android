package org.duckdns.woodysside.campingfinder.di

import org.duckdns.woodysside.campingfinder.BuildConfig
import org.duckdns.woodysside.campingfinder.data.remote.CampingFinderApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // 서버가 필드를 추가해도 앱이 죽지 않는다.
        // 앱은 스토어 심사를 거쳐야 갱신되므로, 구버전 앱이 신버전 응답을 견뎌야 한다.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // 본문 로깅은 디버그 빌드에만. 릴리스에 남기면 응답 내용이 logcat 에 그대로 찍힌다.
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideCampingFinderApi(retrofit: Retrofit): CampingFinderApi =
        retrofit.create(CampingFinderApi::class.java)
}
