package org.duckdns.woodysside.campingfinder.data

import org.duckdns.woodysside.campingfinder.data.local.CampsiteCacheDao
import org.duckdns.woodysside.campingfinder.data.local.CampsiteDetailEntity
import org.duckdns.woodysside.campingfinder.data.local.CampsiteSummaryEntity
import org.duckdns.woodysside.campingfinder.data.local.FilterOptionsEntity
import org.duckdns.woodysside.campingfinder.data.local.SearchResultEntity

/**
 * 메모리 페이크 DAO.
 *
 * <p><b>쿼리를 검증하려는 것이 아니다.</b> 쿼리는 실제 SQLite 위에서
 * {@code CampsiteCacheDaoTest}(androidTest)가 본다. 여기서 보는 것은 리포지토리의 <b>폴백 규칙</b>이다 —
 * 무엇을 잡고 무엇을 던지는가, 어떤 키로 저장하는가.
 *
 * <p>{@code replacePage} 는 <b>일부러 오버라이드하지 않는다.</b> 인터페이스에 본문이 있는
 * 기본 구현이라 그대로 상속하면 실제 조합 로직(지우고 → 요약 upsert → 순서 upsert)이 그대로 돈다.
 * 페이크가 프로덕션과 다른 순서로 동작하면 테스트가 거짓말을 한다.
 *
 * <p>{@code queryKeyOf} 는 {@code private} 이지만 열지 않았다 —
 * <b>DAO 가 그 키를 인자로 받으므로 여기서 그대로 관찰된다.</b>
 * 키 문자열의 형식이 아니라 "같은 조건이면 같은 키" 라는 성질만 검증하면 되고,
 * 형식에 못을 박으면 나중에 키에 필드를 하나 더할 때 의미 없이 빨개진다.
 */
class FakeCampsiteCacheDao : CampsiteCacheDao {

    private val summaries = linkedMapOf<String, CampsiteSummaryEntity>()
    private val searchResults = mutableListOf<SearchResultEntity>()
    private val details = linkedMapOf<String, CampsiteDetailEntity>()
    private var filterOptions: FilterOptionsEntity? = null

    /** `replacePage` 가 받은 queryKey 를 순서대로 기록한다. 17·18 번 테스트가 이걸 본다. */
    val observedQueryKeys = mutableListOf<String>()

    /** 저장공간이 가득 찬 상황. 쓰기가 전부 터진다. */
    var failWrites = false

    /** 캐시가 읽히지 않는 상황(컨버터 실패·DB 손상). 읽기가 전부 터진다. */
    var failReads = false

    private fun checkWrite() {
        if (failWrites) throw IllegalStateException("디스크가 가득 찼다(테스트)")
    }

    private fun checkRead() {
        if (failReads) throw IllegalStateException("캐시를 읽을 수 없다(테스트)")
    }

    override suspend fun upsertSummaries(summaries: List<CampsiteSummaryEntity>) {
        checkWrite()
        summaries.forEach { this.summaries[it.contentId] = it }
    }

    override suspend fun upsertSearchResults(results: List<SearchResultEntity>) {
        checkWrite()
        searchResults.addAll(results)
    }

    override suspend fun deletePage(queryKey: String, page: Int) {
        checkWrite()
        observedQueryKeys.add(queryKey)
        searchResults.removeAll { it.queryKey == queryKey && it.page == page }
    }

    override suspend fun summariesForPage(queryKey: String, page: Int): List<CampsiteSummaryEntity> {
        checkRead()
        return searchResults
            .filter { it.queryKey == queryKey && it.page == page }
            .sortedBy { it.position }
            .mapNotNull { summaries[it.contentId] }
    }

    override suspend fun totalElementsForPage(queryKey: String, page: Int): Long? {
        checkRead()
        return searchResults.firstOrNull { it.queryKey == queryKey && it.page == page }?.totalElements
    }

    override suspend fun fetchedAtForPage(queryKey: String, page: Int): Long? {
        checkRead()
        return searchResults.firstOrNull { it.queryKey == queryKey && it.page == page }?.fetchedAt
    }

    override suspend fun upsertDetail(detail: CampsiteDetailEntity) {
        checkWrite()
        details[detail.contentId] = detail
    }

    override suspend fun detail(contentId: String): CampsiteDetailEntity? {
        checkRead()
        return details[contentId]
    }

    override suspend fun touchDetail(contentId: String, now: Long) {
        checkWrite()
        details[contentId]?.let { details[contentId] = it.copy(lastAccessedAt = now) }
    }

    override suspend fun trimDetails(keep: Int) {
        checkWrite()
        details.values.sortedByDescending { it.lastAccessedAt }.drop(keep).forEach {
            details.remove(it.contentId)
        }
    }

    override suspend fun upsertFilterOptions(entity: FilterOptionsEntity) {
        checkWrite()
        filterOptions = entity
    }

    override suspend fun filterOptions(): FilterOptionsEntity? {
        checkRead()
        return filterOptions
    }

    override suspend fun deleteOrphanSummaries() {
        checkWrite()
        val referenced = searchResults.map { it.contentId }.toSet() + details.keys
        summaries.keys.retainAll(referenced)
    }

    override suspend fun deleteSearchResultsBefore(before: Long) {
        checkWrite()
        searchResults.removeAll { it.fetchedAt < before }
    }

    // ── 테스트가 상태를 직접 심을 때 쓴다 (쓰기 실패 플래그를 우회한다) ──────────

    fun seedDetail(detail: CampsiteDetailEntity) {
        details[detail.contentId] = detail
    }

    fun seedFilterOptions(entity: FilterOptionsEntity) {
        filterOptions = entity
    }

    fun detailSnapshot(contentId: String): CampsiteDetailEntity? = details[contentId]

    /**
     * 저장 시각을 테스트가 아는 값으로 바꾼다.
     *
     * <p>{@code cachePage} 가 {@code System.currentTimeMillis()} 를 쓰기 때문에
     * "캐시에서 읽은 나이" 를 정확히 단정하려면 심어 둔 값이 필요하다.
     * 시계를 주입 가능하게 바꾸는 편이 깔끔하지만, 그건 프로덕션 API 를 테스트 때문에
     * 넓히는 일이라 여기서는 페이크 쪽에서 해결한다.
     */
    fun rewriteFetchedAt(fetchedAt: Long) {
        searchResults.replaceAll { it.copy(fetchedAt = fetchedAt) }
    }
}
