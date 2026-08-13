package org.duckdns.woodysside.campingfinder.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 서버 응답 DTO. 도메인 모델과 분리해 둔다 — 서버 응답 형태가 바뀌어도 화면이 따라 흔들리지 않게.
 *
 * <p>대부분의 필드가 nullable 이다. 원본 공공데이터에 값이 없는 항목이 많고,
 * 서버는 그것을 억지로 채우지 않고 null 로 내려준다.
 */
@Serializable
data class PageResponseDto<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class CampsiteSummaryDto(
    val id: Long,
    val contentId: String,
    val name: String,
    val doNm: String? = null,
    val sigunguNm: String? = null,
    val addr1: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val firstImageUrl: String? = null,
    val petPolicy: String? = null,
    val manageStatus: String? = null,

    /**
     * ★ 부대시설 정보를 아는가.
     *
     * false 면 "시설이 없다" 가 아니라 "정보가 없다" 다. 화면에서 반드시 구분해 보여야 한다 —
     * 감추면 사용자가 "전기 없는 곳" 으로 오해한다. 전체의 28.7% 가 여기 해당한다.
     */
    val sbrsKnown: Boolean = false,

    /** 부대시설 표시명. sbrsKnown 이 false 면 비어 있다. */
    val facilities: List<String> = emptyList(),

    /** 반경 검색일 때만 채워진다. */
    val distanceKm: Double? = null,
)

@Serializable
data class CampsiteDetailDto(
    val contentId: String,
    val name: String,
    val lineIntro: String? = null,
    val intro: String? = null,
    val feature: String? = null,
    val doNm: String? = null,
    val sigunguNm: String? = null,
    val addr1: String? = null,
    val addr2: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tel: String? = null,
    val homepage: String? = null,
    val resveUrl: String? = null,
    val firstImageUrl: String? = null,
    val petPolicy: String? = null,
    val brazierType: String? = null,
    val manageStatus: String? = null,
    val toiletCount: Int? = null,
    val showerCount: Int? = null,
    val washstandCount: Int? = null,

    /** 카테고리(SBRS, LCT ...) → 표시명 목록 */
    val facilities: Map<String, List<String>> = emptyMap(),

    /** ★ 정보가 없는 시설군. 화면에 "정보 없음" 으로 명시해야 한다. */
    val unknownCategories: List<String> = emptyList(),
)

@Serializable
data class FilterOptionDto(
    val code: String,
    val label: String,
)
