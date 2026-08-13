package org.duckdns.woodysside.campingfinder.data.remote

import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteDetailDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteSummaryDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.FilterOptionDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.PageResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 자체 백엔드(camping-finder-api) 호출.
 *
 * <p>고캠핑 공공데이터 API 를 앱에서 직접 부르지 않는다. 이유는 셋이다.
 * <ol>
 *   <li>API 키가 APK 에 박히면 디컴파일로 새어나간다</li>
 *   <li>공공 API 는 일일 호출 한도가 있다 — 앱이 직접 부르면 호출량이 사용자 수에 비례해
 *       설치 몇십 대만으로 한도가 터진다</li>
 *   <li>조합 필터(전기 + 반려동물 + 화로대)를 공공 API 가 지원하지 않는다</li>
 * </ol>
 */
interface CampingFinderApi {

    /**
     * @param includeUnknown 시설 정보가 없는 곳을 포함할지. <b>기본 true 여야 한다.</b>
     *                       false 로 두면 "전기 있는 곳" 검색에서 849곳이 조용히 사라진다.
     */
    @GET("api/campsites")
    suspend fun searchCampsites(
        @Query("keyword") keyword: String? = null,
        @Query("doNm") doNm: String? = null,
        @Query("sbrs") sbrs: List<String>? = null,
        @Query("induty") induty: List<String>? = null,
        @Query("petAllowed") petAllowed: Boolean? = null,
        @Query("includeUnknown") includeUnknown: Boolean = true,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageResponseDto<CampsiteSummaryDto>

    /**
     * 반경 검색.
     *
     * <p>좌표가 없는 캠핑장 9건은 서버가 제외한다 — 지도에 찍을 수 없기 때문이다.
     * 목록 검색에서는 빠지지 않는다.
     */
    @GET("api/campsites")
    suspend fun nearbyCampsites(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double,
        @Query("includeUnknown") includeUnknown: Boolean = true,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): PageResponseDto<CampsiteSummaryDto>

    @GET("api/campsites/{contentId}")
    suspend fun getCampsite(@Path("contentId") contentId: String): CampsiteDetailDto

    /**
     * 필터 항목 메타.
     *
     * <p>앱에 하드코딩하지 않는다. 시설 항목은 공공데이터가 늘리면 늘어나는데,
     * 앱에 박아두면 항목이 하나 늘 때마다 스토어 심사를 다시 받아야 한다.
     */
    @GET("api/filters")
    suspend fun getFilters(): Map<String, List<FilterOptionDto>>
}
