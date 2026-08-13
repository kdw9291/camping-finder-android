package org.duckdns.woodysside.campingfinder

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class CampingFinderApplication : Application(), SingletonImageLoader.Factory {

    /** 앱 전체가 쓰는 OkHttpClient 를 이미지 로딩에도 공유한다. 커넥션 풀을 두 벌 만들 이유가 없다. */
    @Inject
    lateinit var okHttpClient: OkHttpClient

    /**
     * 네트워크 페처를 명시적으로 등록한다.
     *
     * <p>Coil 3 는 `coil-network-okhttp` 아티팩트만 넣어도 자동 등록되지만,
     * 그 경로에 기대면 이미지가 안 뜰 때 원인 찾기가 어렵다.
     * 명시하면 동작이 코드에 드러나고, 위처럼 OkHttpClient 를 공유할 수도 있다.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            .crossfade(true)
            .build()
}
