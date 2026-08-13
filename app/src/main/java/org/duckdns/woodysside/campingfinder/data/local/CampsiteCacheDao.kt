package org.duckdns.woodysside.campingfinder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CampsiteCacheDao {

    // ── 검색 결과 ────────────────────────────────────────────────────────────

    /**
     * 한 페이지를 통째로 교체한다.
     *
     * <p>{@code @Transaction} 이 붙은 이유: 지우고 넣는 사이에 앱이 죽으면
     * 그 페이지만 사라진 캐시가 남는다. 오프라인에서 목록 중간이 비는 것은
     * 캐시가 아예 없는 것보다 나쁘다 — 사용자는 그것을 "검색 결과가 원래 그렇다" 고 읽는다.
     */
    @Transaction
    suspend fun replacePage(
        queryKey: String,
        page: Int,
        summaries: List<CampsiteSummaryEntity>,
        totalElements: Long,
        fetchedAt: Long,
    ) {
        deletePage(queryKey, page)
        upsertSummaries(summaries)
        upsertSearchResults(
            summaries.mapIndexed { index, summary ->
                SearchResultEntity(
                    queryKey = queryKey,
                    page = page,
                    position = index,
                    contentId = summary.contentId,
                    totalElements = totalElements,
                    fetchedAt = fetchedAt,
                )
            },
        )
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummaries(summaries: List<CampsiteSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchResults(results: List<SearchResultEntity>)

    @Query("DELETE FROM search_result WHERE queryKey = :queryKey AND page = :page")
    suspend fun deletePage(queryKey: String, page: Int)

    /**
     * 저장된 페이지를 원래 순서대로 읽는다.
     *
     * <p>{@code ORDER BY r.position} 이 핵심이다. 빼면 SQLite 가 임의 순서로 줄 수 있다 —
     * 서버가 정렬한 순서(이름순·거리순)가 무너지고, 사용자는 목록이 매번 뒤바뀌는 앱을 본다.
     *
     * <p>⚠️ <b>지금은 이 줄을 지워도 테스트가 통과한다. 그래도 지우지 마라.</b>
     * 실측한 실행 계획이 이렇다:
     *
     * <pre>
     * SEARCH r USING INDEX sqlite_autoindex_search_result_1 (queryKey=? AND page=?)
     * SEARCH s USING INDEX sqlite_autoindex_campsite_summary_1 (contentId=?)
     * </pre>
     *
     * 복합 PK 가 {@code (queryKey, page, position)} 이라 그 자동 인덱스를 훑으면
     * <b>우연히</b> position 순서로 나온다. 즉 지금 순서가 맞는 것은 PK 컬럼 순서 덕이지
     * 우리가 요구했기 때문이 아니다. PK 순서를 바꾸거나 인덱스를 더하거나
     * 플래너가 다른 계획을 고르는 순간 조용히 어긋난다 — SQL 은 {@code ORDER BY} 없이
     * 어떤 순서도 약속하지 않는다.
     */
    @Query(
        """
        SELECT s.* FROM campsite_summary s
        INNER JOIN search_result r ON s.contentId = r.contentId
        WHERE r.queryKey = :queryKey AND r.page = :page
        ORDER BY r.position
        """,
    )
    suspend fun summariesForPage(queryKey: String, page: Int): List<CampsiteSummaryEntity>

    @Query(
        "SELECT totalElements FROM search_result WHERE queryKey = :queryKey AND page = :page LIMIT 1",
    )
    suspend fun totalElementsForPage(queryKey: String, page: Int): Long?

    @Query("SELECT fetchedAt FROM search_result WHERE queryKey = :queryKey AND page = :page LIMIT 1")
    suspend fun fetchedAtForPage(queryKey: String, page: Int): Long?

    // ── 상세 ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDetail(detail: CampsiteDetailEntity)

    @Query("SELECT * FROM campsite_detail WHERE contentId = :contentId")
    suspend fun detail(contentId: String): CampsiteDetailEntity?

    /**
     * 열었다는 사실만 기록한다.
     *
     * <p>⚠️ {@code cachedAt} 을 함께 갱신하면 안 된다. 그건 <b>받은</b> 시각이고,
     * 배너가 말하는 나이의 근거다. 여기서 건드리면 3주 전에 받은 운영시간을 보면서
     * "오늘 받은 것" 이라는 배너가 뜬다.
     */
    @Query("UPDATE campsite_detail SET lastAccessedAt = :now WHERE contentId = :contentId")
    suspend fun touchDetail(contentId: String, now: Long)

    /**
     * 최근에 연 {@code keep} 건만 남긴다.
     *
     * <p>기준이 {@code cachedAt}(받은 시각)이 아니라 {@code lastAccessedAt}(마지막으로 연 시각)인 이유:
     * 다음 주에 갈 캠핑장은 오래전에 받았더라도 계속 열어 본 곳이다.
     * 받은 시각으로 지우면 정작 가려던 곳이 먼저 지워진다.
     */
    @Query(
        """
        DELETE FROM campsite_detail WHERE contentId NOT IN (
            SELECT contentId FROM campsite_detail ORDER BY lastAccessedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimDetails(keep: Int)

    // ── 필터 항목 ───────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilterOptions(entity: FilterOptionsEntity)

    @Query("SELECT * FROM filter_options WHERE id = ${FilterOptionsEntity.SINGLETON_ID}")
    suspend fun filterOptions(): FilterOptionsEntity?

    // ── 정리 ────────────────────────────────────────────────────────────────

    /**
     * 어떤 검색 결과에도 속하지 않고 상세로도 저장되지 않은 요약을 지운다.
     *
     * <p>검색을 여러 번 하면 옛 검색의 요약이 남는다. 페이지 교체는 {@code search_result} 만
     * 지우므로 요약 자체는 고아로 남는다. 이걸 안 치우면 캐시가 단조 증가한다.
     */
    @Query(
        """
        DELETE FROM campsite_summary
        WHERE contentId NOT IN (SELECT contentId FROM search_result)
          AND contentId NOT IN (SELECT contentId FROM campsite_detail)
        """,
    )
    suspend fun deleteOrphanSummaries()

    /** 오래된 검색 결과를 지운다. 상세는 건드리지 않는다 — 현장에서 필요한 것은 상세다. */
    @Query("DELETE FROM search_result WHERE fetchedAt < :before")
    suspend fun deleteSearchResultsBefore(before: Long)
}
