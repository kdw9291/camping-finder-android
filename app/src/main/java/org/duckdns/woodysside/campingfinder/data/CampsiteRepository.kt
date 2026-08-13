package org.duckdns.woodysside.campingfinder.data

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.duckdns.woodysside.campingfinder.data.local.CampingFinderDatabase
import org.duckdns.woodysside.campingfinder.data.local.CampsiteCacheDao
import org.duckdns.woodysside.campingfinder.data.local.FilterOptionsEntity
import org.duckdns.woodysside.campingfinder.data.local.toDto
import org.duckdns.woodysside.campingfinder.data.local.toEntity
import org.duckdns.woodysside.campingfinder.data.remote.CampingFinderApi
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteDetailDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteSummaryDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.FilterOptionDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.PageResponseDto
import org.duckdns.woodysside.campingfinder.ui.campsite.CampsiteFilter
import org.duckdns.woodysside.campingfinder.ui.campsite.FilterOptions

/** 목록 화면이 필요로 하는 것만. 페이지 번호·크기는 호출자가 이미 알고 있다. */
data class CampsitePage(
    val content: List<CampsiteSummaryDto>,
    val totalElements: Long,
)

/**
 * 캠핑장 데이터 접근.
 *
 * <h3>네트워크 우선, 캐시 폴백</h3>
 *
 * 캐시 우선이 아니다. 캠핑장의 운영 상태(운영중/폐업)와 예약 URL 은 실제로 바뀌고,
 * 폐업한 곳으로 두 시간을 운전해 가는 것이 이 앱이 만들 수 있는 최악의 결과다.
 * 신호가 있으면 최신을 보여준다. 없을 때만 저장해 둔 것을 꺼낸다.
 *
 * <h3>오프라인에서 필터는 동작하지 않는다</h3>
 *
 * 조합 필터의 3-state 판정(있음/없음/<b>미상</b>)은 서버에만 있다. SQLite 에 옮겨 적으면
 * 같은 규칙이 두 벌이 되고, 두 벌은 반드시 어긋난다. 어긋나는 순간 앱은
 * "전기 있는 곳"이라며 849곳을 조용히 빼먹는다 — 이 프로젝트가 존재하는 이유가 그 실패다.
 *
 * 그래서 오프라인에서는 <b>이미 받아 둔 검색의 결과만</b> 재생한다. 새 조건을 계산하지 않는다.
 * 저장된 적 없는 조건을 걸면 결과가 없고, 화면은 그것을 "캠핑장이 없다"가 아니라
 * "저장된 결과가 없다"고 말해야 한다.
 */
@Singleton
class CampsiteRepository @Inject constructor(
    private val api: CampingFinderApi,
    private val cache: CampsiteCacheDao,
) {

    // ── 검색 ────────────────────────────────────────────────────────────────

    suspend fun search(
        keyword: String?,
        filter: CampsiteFilter = CampsiteFilter(),
        page: Int,
        size: Int = PAGE_SIZE,
    ): Cached<CampsitePage> = withContext(Dispatchers.IO) {
        val queryKey = queryKeyOf(keyword, filter)

        try {
            val response = api.searchCampsites(
                keyword = keyword?.takeIf { it.isNotBlank() },
                // 빈 집합을 넘기면 Retrofit 이 빈 쿼리 파라미터를 만든다. null 로 보내 아예 생략한다.
                sbrs = filter.sbrs.takeIf { it.isNotEmpty() }?.toList(),
                induty = filter.induty.takeIf { it.isNotEmpty() }?.toList(),
                petAllowed = filter.petAllowed.takeIf { it },
                includeUnknown = filter.includeUnknown,
                page = page,
                size = size,
            )
            cachePage(queryKey, page, response)
            Cached.network(CampsitePage(response.content, response.totalElements))
        } catch (e: IOException) {
            // 네트워크 계층의 실패만 캐시로 넘어간다.
            // HTTP 4xx/5xx(HttpException)는 서버가 대답한 것이므로 그대로 던진다 —
            // 잘못된 요청을 오래된 캐시로 덮으면 버그가 보이지 않는다.
            fallbackToCache(e) { readPageFromCache(queryKey, page) } ?: throw e
        }
    }

    private suspend fun cachePage(
        queryKey: String,
        page: Int,
        response: PageResponseDto<CampsiteSummaryDto>,
    ) {
        val now = System.currentTimeMillis()
        // ⚠️ 캐시 쓰기 실패는 삼킨다. 저장공간이 가득 찼다는 이유로
        //    이미 손에 들어온 검색 결과를 못 보여주는 것은 말이 안 된다.
        swallowCacheWriteFailure {
            cache.replacePage(
                queryKey = queryKey,
                page = page,
                summaries = response.content.map { it.toEntity(now) },
                totalElements = response.totalElements,
                fetchedAt = now,
            )
            // 첫 페이지를 새로 받았다는 것은 검색을 새로 했다는 뜻이다. 이때만 청소한다.
            if (page == 0) {
                cache.deleteSearchResultsBefore(
                    now - CampingFinderDatabase.SEARCH_RESULT_RETENTION_MILLIS,
                )
                cache.deleteOrphanSummaries()
            }
        }
    }

    /**
     * ★ 캐시 읽기가 실패해도 <b>원본 네트워크 예외를 가리지 않는다.</b>
     *
     * <p>사용자가 알아야 하는 것은 "네트워크에 연결할 수 없다" 이지
     * {@code SerializationException} 이나 {@code SQLiteException} 이 아니다.
     * 캐시는 서버 응답의 파생본이므로(M5 D5) <b>읽히지 않는 캐시는 캐시가 없는 것과 같다.</b>
     *
     * <p>정확히 신호가 없는 그 순간에 앱이 낯선 예외로 죽는 것이,
     * 이 캐시 기능이 막으려고 만들어진 바로 그 상황이다.
     *
     * <p>실패 자체는 감추지 않는다 — {@code addSuppressed} 로 원본 예외에 붙여 둔다.
     * 죽이지 않을 뿐, 조용히 사라지게 두지는 않는다.
     */
    private suspend fun <T> fallbackToCache(
        // ★ 타입을 IOException 으로 좁게 잡는다. Exception 으로 넓히면
        //   `catch (e: Exception)` 으로 바꿔도 컴파일이 되어 D3(HTTP 오류는 폴백 금지)가
        //   조용히 무너진다. 실제로 변이 테스트에서 이 좁은 타입이 그 변경을 막았다.
        networkFailure: IOException,
        read: suspend () -> T?,
    ): T? = try {
        read()
    } catch (cancellation: CancellationException) {
        // ⚠️ 취소는 실패가 아니다. 여기서 삼키면 화면을 떠난 뒤에도 코루틴이 계속 돈다.
        throw cancellation
    } catch (cacheFailure: Exception) {
        networkFailure.addSuppressed(cacheFailure)
        null
    }

    /**
     * 캐시 쓰기 실패를 삼킨다.
     *
     * <p>{@code runCatching} 을 쓰지 않는 이유: 그건 {@link CancellationException} 까지 잡는다.
     * 목록 화면은 새로고침할 때마다 이전 검색을 {@code cancel()} 하므로 실제로 지나가는 경로다.
     * 취소를 삼키면 구조적 동시성이 깨진다.
     */
    private suspend fun swallowCacheWriteFailure(write: suspend () -> Unit) {
        try {
            write()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // 의도적으로 무시한다. 위 주석 참고.
        }
    }

    private suspend fun readPageFromCache(queryKey: String, page: Int): Cached<CampsitePage>? {
        val summaries = cache.summariesForPage(queryKey, page)
        if (summaries.isEmpty()) return null

        val fetchedAt = cache.fetchedAtForPage(queryKey, page) ?: return null
        val total = cache.totalElementsForPage(queryKey, page) ?: summaries.size.toLong()

        return Cached.cache(
            CampsitePage(summaries.map { it.toDto() }, total),
            fetchedAt = fetchedAt,
        )
    }

    /**
     * 검색 조건을 캐시 키 하나로 만든다.
     *
     * <p>집합은 <b>반드시 정렬</b>한다. {@code Set} 의 순회 순서는 보장되지 않아서,
     * 정렬하지 않으면 같은 조건이 다른 키가 되고 캐시가 조용히 빗나간다.
     * "가끔 오프라인에서 결과가 안 나오는" 재현 안 되는 버그가 이렇게 만들어진다.
     *
     * <p>{@code includeUnknown} 도 키에 넣는다. 이 값 하나로 결과가 849곳 달라진다.
     */
    private fun queryKeyOf(keyword: String?, filter: CampsiteFilter): String = buildString {
        append("k=").append(keyword?.trim().orEmpty())
        append("|sbrs=").append(filter.sbrs.sorted().joinToString(","))
        append("|induty=").append(filter.induty.sorted().joinToString(","))
        append("|pet=").append(filter.petAllowed)
        append("|unknown=").append(filter.includeUnknown)
    }

    // ── 상세 ────────────────────────────────────────────────────────────────

    /**
     * 상세.
     *
     * <p>오프라인에서 실제로 열리는 화면이 여기다. 목록은 집에서 보고,
     * 주소·전화번호·화장실 수는 신호가 없는 현장에서 본다.
     */
    suspend fun detail(contentId: String): Cached<CampsiteDetailDto> = withContext(Dispatchers.IO) {
        try {
            val detail = api.getCampsite(contentId)
            val now = System.currentTimeMillis()
            swallowCacheWriteFailure {
                cache.upsertDetail(detail.toEntity(now))
                cache.trimDetails(CampingFinderDatabase.MAX_CACHED_DETAILS)
            }
            Cached.network(detail)
        } catch (e: IOException) {
            val cached = fallbackToCache(e) { cache.detail(contentId) } ?: throw e
            // 열었다는 사실을 남긴다. LRU 정리에서 살아남아야 할 것은 자주 여는 곳이다.
            swallowCacheWriteFailure { cache.touchDetail(contentId, System.currentTimeMillis()) }
            // 나이는 받은 시각(cachedAt)이다. 방금 갱신한 lastAccessedAt 을 쓰면
            // 배너가 항상 "오늘 받은 것" 이라고 거짓말한다.
            Cached.cache(cached.toDto(), fetchedAt = cached.cachedAt)
        }
    }

    // ── 필터 항목 ───────────────────────────────────────────────────────────

    /**
     * 필터 항목 메타.
     *
     * <p>캐시하지 않으면 오프라인에서 필터 시트가 빈 화면이 된다.
     * 목록은 보이는데 필터만 비어 있으면 사용자는 앱이 고장 났다고 읽는다.
     */
    suspend fun filterOptions(): FilterOptions = withContext(Dispatchers.IO) {
        try {
            val response = api.getFilters()
            swallowCacheWriteFailure {
                cache.upsertFilterOptions(
                    FilterOptionsEntity(
                        payload = filterJson.encodeToString(filterOptionsSerializer, response),
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
            }
            FilterOptions.from(response)
        } catch (e: IOException) {
            // ★ 디코딩까지 폴백 안에서 한다.
            //    payload 는 TEXT 컬럼이라 DB 스키마 버전이 지켜 주지 않는다 —
            //    앱을 업데이트해 FilterOptionDto 가 바뀌어도 파괴적 마이그레이션이 발동하지 않고,
            //    구버전이 쓴 JSON 을 신버전이 읽는 일이 실제로 생긴다.
            val cached = fallbackToCache(e) {
                cache.filterOptions()?.let {
                    filterJson.decodeFromString(filterOptionsSerializer, it.payload)
                }
            } ?: throw e
            FilterOptions.from(cached)
        }
    }

    // ── 지도 ────────────────────────────────────────────────────────────────

    /**
     * 반경 검색.
     *
     * <p><b>캐시하지 않는다.</b> 지도 타일은 네이버 서버에서 오고 오프라인에서는 받을 수 없다.
     * 마커만 저장해 봐야 빈 회색 배경 위에 점이 떠 있을 뿐이라 아무 쓸모가 없다.
     * 저장할 수 있다고 저장하는 것이 아니라, 저장해서 쓸모가 있을 때만 저장한다.
     */
    suspend fun nearby(
        lat: Double,
        lng: Double,
        radiusKm: Double,
    ): PageResponseDto<CampsiteSummaryDto> = withContext(Dispatchers.IO) {
        api.nearbyCampsites(lat = lat, lng = lng, radiusKm = radiusKm, size = MAP_MARKER_LIMIT)
    }

    companion object {
        const val PAGE_SIZE = 20

        /** 서버가 허용하는 한 페이지 상한. 마커를 이보다 많이 찍으면 지도도 사람도 못 읽는다. */
        const val MAP_MARKER_LIMIT = 100

        /**
         * 캐시에 넣고 캐시에서 읽는 payload 전용.
         *
         * <p>{@code ignoreUnknownKeys = true} 인 이유는 관대해서가 아니라
         * <b>읽는 쪽이 쓴 쪽과 다른 버전의 앱이기 때문이다.</b>
         * 이 payload 는 TEXT 컬럼 한 칸이라 Room 의 스키마 버전이 형태를 지켜 주지 않는다 —
         * {@code FilterOptionDto} 에서 필드를 하나 빼고 배포하면, 구버전이 저장해 둔 JSON 이
         * 신버전에서 읽히지 않게 되고 <b>정확히 오프라인에서만</b> 필터 시트가 무너진다.
         *
         * <p>필수 필드가 빠진 경우는 여전히 실패한다. 그건 관대하게 넘길 일이 아니다.
         */
        private val filterJson = Json { ignoreUnknownKeys = true }
        private val filterOptionsSerializer =
            MapSerializer(String.serializer(), ListSerializer(FilterOptionDto.serializer()))
    }
}
