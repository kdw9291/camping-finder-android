package org.duckdns.woodysside.campingfinder.data

/**
 * 값과 함께 <b>그 값이 어디서 왔는지</b>를 옮긴다.
 *
 * <p>이 래퍼가 필요한 이유는 이 프로젝트의 중심축과 같다 —
 * <b>모르는 것을 아는 척하지 않는다.</b>
 * 부대시설의 빈 값을 "시설 없음"으로 바꿔 말하지 않은 것처럼,
 * 3일 전에 받아 둔 목록을 방금 받은 것처럼 보여주지 않는다.
 *
 * <p>화면은 {@link #isFromCache} 로 배너를 띄우고 {@link #ageMillis} 로 나이를 말한다.
 */
data class Cached<T>(
    val value: T,
    val source: Source,
    /** 이 값을 <b>서버에서</b> 받은 시각. 캐시에서 읽은 시각이 아니다. */
    val fetchedAt: Long,
) {
    enum class Source { NETWORK, CACHE }

    val isFromCache: Boolean get() = source == Source.CACHE

    val ageMillis: Long get() = (System.currentTimeMillis() - fetchedAt).coerceAtLeast(0)

    companion object {
        fun <T> network(value: T): Cached<T> =
            Cached(value, Source.NETWORK, System.currentTimeMillis())

        fun <T> cache(value: T, fetchedAt: Long): Cached<T> =
            Cached(value, Source.CACHE, fetchedAt)
    }
}
