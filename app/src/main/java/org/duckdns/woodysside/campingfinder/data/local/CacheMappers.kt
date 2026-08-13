package org.duckdns.woodysside.campingfinder.data.local

import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteDetailDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteSummaryDto

/**
 * DTO ↔ 엔티티 변환.
 *
 * <p>둘을 같은 클래스로 합치지 않았다. DTO 에는 {@code distanceKm} 처럼 <b>요청에 따라
 * 달라지는</b> 값이 있는데, 그것을 캐시에 저장하면 다음에 다른 위치에서 꺼냈을 때
 * 거짓말이 된다. 캐시에는 캠핑장 자체의 속성만 남긴다.
 */

fun CampsiteSummaryDto.toEntity(cachedAt: Long): CampsiteSummaryEntity = CampsiteSummaryEntity(
    contentId = contentId,
    id = id,
    name = name,
    doNm = doNm,
    sigunguNm = sigunguNm,
    addr1 = addr1,
    latitude = latitude,
    longitude = longitude,
    firstImageUrl = firstImageUrl,
    petPolicy = petPolicy,
    manageStatus = manageStatus,
    sbrsKnown = sbrsKnown,
    facilities = facilities,
    cachedAt = cachedAt,
)

fun CampsiteSummaryEntity.toDto(): CampsiteSummaryDto = CampsiteSummaryDto(
    id = id,
    contentId = contentId,
    name = name,
    doNm = doNm,
    sigunguNm = sigunguNm,
    addr1 = addr1,
    latitude = latitude,
    longitude = longitude,
    firstImageUrl = firstImageUrl,
    petPolicy = petPolicy,
    manageStatus = manageStatus,
    sbrsKnown = sbrsKnown,
    facilities = facilities,
    // 거리는 저장하지 않는다. 반경 검색의 결과값이지 캠핑장의 속성이 아니다.
    distanceKm = null,
)

fun CampsiteDetailDto.toEntity(now: Long): CampsiteDetailEntity = CampsiteDetailEntity(
    contentId = contentId,
    name = name,
    lineIntro = lineIntro,
    intro = intro,
    feature = feature,
    doNm = doNm,
    sigunguNm = sigunguNm,
    addr1 = addr1,
    addr2 = addr2,
    latitude = latitude,
    longitude = longitude,
    tel = tel,
    homepage = homepage,
    resveUrl = resveUrl,
    firstImageUrl = firstImageUrl,
    petPolicy = petPolicy,
    brazierType = brazierType,
    manageStatus = manageStatus,
    toiletCount = toiletCount,
    showerCount = showerCount,
    washstandCount = washstandCount,
    facilities = facilities,
    unknownCategories = unknownCategories,
    cachedAt = now,
    lastAccessedAt = now,
)

fun CampsiteDetailEntity.toDto(): CampsiteDetailDto = CampsiteDetailDto(
    contentId = contentId,
    name = name,
    lineIntro = lineIntro,
    intro = intro,
    feature = feature,
    doNm = doNm,
    sigunguNm = sigunguNm,
    addr1 = addr1,
    addr2 = addr2,
    latitude = latitude,
    longitude = longitude,
    tel = tel,
    homepage = homepage,
    resveUrl = resveUrl,
    firstImageUrl = firstImageUrl,
    petPolicy = petPolicy,
    brazierType = brazierType,
    manageStatus = manageStatus,
    toiletCount = toiletCount,
    showerCount = showerCount,
    washstandCount = washstandCount,
    facilities = facilities,
    unknownCategories = unknownCategories,
)
