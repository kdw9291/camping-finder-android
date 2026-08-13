package org.duckdns.woodysside.campingfinder.ui.campsite

import org.duckdns.woodysside.campingfinder.data.Cached

/**
 * 화면에 "이건 저장해 둔 정보다" 를 알리기 위한 최소한의 상태.
 *
 * <p>{@code Cached<T>} 를 UiState 에 그대로 넣지 않는다. 화면이 알아야 하는 것은
 * 값의 출처가 아니라 <b>사용자에게 무엇을 말해야 하는가</b> 뿐이다.
 * ViewModel 이 그 번역을 맡아야 Composable 이 데이터 계층 타입을 알지 않아도 된다.
 *
 * @param ageDays 며칠 전에 받은 것인가. 0 이면 오늘.
 */
data class CacheNotice(val ageDays: Int) {
    companion object {
        /**
         * 네트워크에서 온 값이면 {@code null}. 화면에 아무것도 띄우지 않는다.
         *
         * <p>캐시에서 왔더라도 방금 받은 것(같은 날)이면 나이를 말할 필요가 없다.
         * 매번 배너를 띄우면 사용자는 곧 그것을 읽지 않게 되고,
         * 정말 오래된 정보일 때도 못 알아챈다.
         */
        fun from(cached: Cached<*>): CacheNotice? {
            if (!cached.isFromCache) return null
            return CacheNotice(ageDays = (cached.ageMillis / DAY_MILLIS).toInt())
        }

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
