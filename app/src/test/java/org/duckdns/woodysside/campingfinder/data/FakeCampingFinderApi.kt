package org.duckdns.woodysside.campingfinder.data

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.duckdns.woodysside.campingfinder.data.remote.CampingFinderApi
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteDetailDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteSummaryDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.FilterOptionDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.PageResponseDto
import retrofit2.HttpException
import retrofit2.Response

/**
 * 페이크 서버.
 *
 * <p>실패를 <b>두 종류로 나눠</b> 흉내 낸다. 이 구분이 D3 의 전부다 —
 * {@code IOException}(연결 자체가 안 됨)은 캐시로 폴백하지만,
 * {@code HttpException}(서버가 4xx/5xx 로 대답함)은 폴백하지 않고 던진다.
 * 잘못된 요청을 오래된 캐시로 덮으면 그 버그는 영원히 안 보인다.
 */
class FakeCampingFinderApi : CampingFinderApi {

    sealed interface Behavior {
        data class Ok(
            val page: PageResponseDto<CampsiteSummaryDto> = PageResponseDto(),
            val detail: CampsiteDetailDto? = null,
            val filters: Map<String, List<FilterOptionDto>> = emptyMap(),
        ) : Behavior

        /** 비행기 모드·산속. 연결 자체가 안 된다. */
        data object Offline : Behavior

        /** 서버가 대답했다. 캐시로 덮으면 안 된다. */
        data class Http(val code: Int) : Behavior
    }

    var behavior: Behavior = Behavior.Ok()

    var callCount = 0
        private set

    private fun <T> resolve(pick: (Behavior.Ok) -> T?): T {
        callCount++
        return when (val b = behavior) {
            is Behavior.Ok -> pick(b) ?: error("페이크에 응답이 설정되지 않았다")
            Behavior.Offline -> throw IOException("연결할 수 없다(테스트)")
            is Behavior.Http -> throw HttpException(
                Response.error<Any>(b.code, "".toResponseBody("application/json".toMediaTypeOrNull())),
            )
        }
    }

    override suspend fun searchCampsites(
        keyword: String?,
        doNm: String?,
        sbrs: List<String>?,
        induty: List<String>?,
        petAllowed: Boolean?,
        includeUnknown: Boolean,
        page: Int,
        size: Int,
    ): PageResponseDto<CampsiteSummaryDto> = resolve { it.page }

    override suspend fun nearbyCampsites(
        lat: Double,
        lng: Double,
        radiusKm: Double,
        includeUnknown: Boolean,
        page: Int,
        size: Int,
    ): PageResponseDto<CampsiteSummaryDto> = resolve { it.page }

    override suspend fun getCampsite(contentId: String): CampsiteDetailDto = resolve { it.detail }

    override suspend fun getFilters(): Map<String, List<FilterOptionDto>> = resolve { it.filters }
}
