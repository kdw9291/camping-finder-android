package org.duckdns.woodysside.campingfinder.ui.campsite

import org.duckdns.woodysside.campingfinder.data.remote.dto.FilterOptionDto

/**
 * 검색 필터 상태.
 *
 * @param includeUnknown ★ 시설 정보가 없는 곳을 포함할지. <b>기본 true.</b>
 *                       부대시설의 28.7%가 빈 값이라, false 로 두면
 *                       "전기 있는 곳" 검색에서 849곳이 조용히 사라진다.
 *                       그 849곳은 전기가 없는 게 아니라 <b>정보가 없는</b> 곳이다.
 */
data class CampsiteFilter(
    val sbrs: Set<String> = emptySet(),
    val induty: Set<String> = emptySet(),
    val petAllowed: Boolean = false,
    val includeUnknown: Boolean = true,
) {
    val activeCount: Int
        get() = sbrs.size + induty.size + (if (petAllowed) 1 else 0)

    val isEmpty: Boolean
        get() = activeCount == 0

    fun toggleSbrs(code: String): CampsiteFilter =
        copy(sbrs = if (code in sbrs) sbrs - code else sbrs + code)

    fun toggleInduty(code: String): CampsiteFilter =
        copy(induty = if (code in induty) induty - code else induty + code)

    fun clear(): CampsiteFilter = CampsiteFilter(includeUnknown = includeUnknown)
}

/** {@code /api/filters} 응답. 앱에 하드코딩하지 않고 서버에서 받는다. */
data class FilterOptions(
    val sbrs: List<FilterOptionDto> = emptyList(),
    val induty: List<FilterOptionDto> = emptyList(),
) {
    companion object {
        fun from(response: Map<String, List<FilterOptionDto>>): FilterOptions = FilterOptions(
            sbrs = response["SBRS"].orEmpty(),
            induty = response["INDUTY"].orEmpty(),
        )
    }
}
