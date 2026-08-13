package org.duckdns.woodysside.campingfinder.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 오프라인 캐시 스키마.
 *
 * <p><b>이 DB 는 서버 응답의 파생본이다.</b> 통째로 날아가도 신호가 잡히는 순간 복구된다.
 * 백엔드에서 Supabase 를 "공공데이터의 파생본"으로 보고 EC2 를 무상태로 만든 것과 같은 판단이다.
 * 그래서 마이그레이션을 쓰지 않고 파괴적으로 재생성한다 (DatabaseModule 참고).
 *
 * <p>⚠️ 즐겨찾기처럼 <b>사용자가 만든</b> 데이터가 들어오면 이 전제가 깨진다.
 * 그 테이블은 파생본이 아니라 원본이므로 실제 마이그레이션을 써야 한다.
 */

/**
 * 캠핑장 요약. 목록 화면에 필요한 것.
 *
 * <p>{@code contentId} 를 기본키로 쓴다. 서버의 {@code id}(자체 시퀀스)가 아니라 —
 * 서버 DB 를 재적재하면 id 는 바뀌지만 contentId 는 공공데이터의 식별자라 그대로다.
 * 앱 캐시가 서버의 내부 사정에 흔들릴 이유가 없다.
 */
@Entity(tableName = "campsite_summary")
data class CampsiteSummaryEntity(
    @PrimaryKey val contentId: String,
    val id: Long,
    val name: String,
    val doNm: String?,
    val sigunguNm: String?,
    val addr1: String?,
    val latitude: Double?,
    val longitude: Double?,
    val firstImageUrl: String?,
    val petPolicy: String?,
    val manageStatus: String?,

    /** ★ 부대시설 정보를 아는가. false 는 "없다" 가 아니라 "모른다" 다. */
    val sbrsKnown: Boolean,
    val facilities: List<String>,

    val cachedAt: Long,
)

/**
 * 검색 결과의 순서.
 *
 * <p>요약 자체는 {@link CampsiteSummaryEntity} 에 한 벌만 두고, 여기에는 "어떤 검색의
 * 몇 번째였는가" 만 남긴다. 같은 캠핑장이 여러 검색에 걸려도 요약이 중복 저장되지 않는다.
 *
 * <p>{@code totalElements} 와 {@code fetchedAt} 은 페이지마다 같은 값이 반복되지만
 * 정규화하지 않았다. 테이블 하나를 더 만들어 조인할 만큼의 이득이 없고,
 * 페이지 단위로 통째 교체하므로 불일치가 생기지 않는다.
 */
@Entity(
    tableName = "search_result",
    primaryKeys = ["queryKey", "page", "position"],
    indices = [Index("contentId")],
)
data class SearchResultEntity(
    val queryKey: String,
    val page: Int,
    val position: Int,
    val contentId: String,
    val totalElements: Long,
    val fetchedAt: Long,
)

/**
 * 캠핑장 상세.
 *
 * <p><b>오프라인에서 실제로 필요한 것은 이쪽이다.</b> 목록은 집에서 보고,
 * 상세(주소·전화번호·화장실 수·운영기간)는 신호가 없는 현장에서 본다.
 *
 * @param lastAccessedAt LRU 정리 기준. 마지막으로 <b>연</b> 시각이지 받은 시각이 아니다.
 */
@Entity(tableName = "campsite_detail")
data class CampsiteDetailEntity(
    @PrimaryKey val contentId: String,
    val name: String,
    val lineIntro: String?,
    val intro: String?,
    val feature: String?,
    val doNm: String?,
    val sigunguNm: String?,
    val addr1: String?,
    val addr2: String?,
    val latitude: Double?,
    val longitude: Double?,
    val tel: String?,
    val homepage: String?,
    val resveUrl: String?,
    val firstImageUrl: String?,
    val petPolicy: String?,
    val brazierType: String?,
    val manageStatus: String?,
    val toiletCount: Int?,
    val showerCount: Int?,
    val washstandCount: Int?,
    val facilities: Map<String, List<String>>,

    /** ★ 정보가 없는 시설군. 이것까지 저장해야 오프라인에서도 "정보 없음"을 말할 수 있다. */
    val unknownCategories: List<String>,

    val cachedAt: Long,
    val lastAccessedAt: Long,
)

/**
 * 필터 항목 메타.
 *
 * <p>캐시하지 않으면 오프라인에서 필터 시트가 <b>빈 화면</b>이 된다.
 * 목록은 캐시되어 보이는데 필터만 비어 있으면 사용자는 앱이 고장 났다고 판단한다.
 *
 * <p>항목이 20여 개뿐이라 행으로 쪼개지 않고 JSON 한 덩이로 둔다.
 * 부분 갱신이 없고 통째로 받아 통째로 바꾸는 데이터다.
 */
@Entity(tableName = "filter_options")
data class FilterOptionsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val payload: String,
    val fetchedAt: Long,
) {
    companion object {
        /** 행이 하나뿐인 테이블. 기본키를 상수로 고정해 항상 덮어쓰게 한다. */
        const val SINGLETON_ID = 0
    }
}
