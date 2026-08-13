package org.duckdns.woodysside.campingfinder.data

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.duckdns.woodysside.campingfinder.data.local.CampsiteDetailEntity
import org.duckdns.woodysside.campingfinder.data.local.CampsiteSummaryEntity
import org.duckdns.woodysside.campingfinder.data.local.FilterOptionsEntity
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteSummaryDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.FilterOptionDto
import org.duckdns.woodysside.campingfinder.data.remote.dto.PageResponseDto
import org.duckdns.woodysside.campingfinder.ui.campsite.CampsiteFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * 리포지토리의 폴백 규칙.
 *
 * <p>DAO 쿼리는 여기서 보지 않는다 — 그건 실제 SQLite 위의 {@code CampsiteCacheDaoTest} 몫이다.
 * 여기서 지키는 것은 <b>무엇을 잡고 무엇을 던지는가</b> 하나다.
 *
 * <p>그 판단이 틀리면 증상이 조용하다. 오프라인에서 결과가 "가끔" 안 나오거나(키 불일치),
 * 서버가 500 을 주는데 앱은 3주 전 캐시를 최신인 양 보여주거나(D3 위반) 한다.
 * 둘 다 사용자 제보로는 절대 재현되지 않는 종류다.
 */
class CampsiteRepositoryTest {

    private lateinit var api: FakeCampingFinderApi
    private lateinit var cache: FakeCampsiteCacheDao
    private lateinit var repository: CampsiteRepository

    @Before
    fun setUp() {
        api = FakeCampingFinderApi()
        cache = FakeCampsiteCacheDao()
        repository = CampsiteRepository(api, cache)
    }

    private fun summaryDto(contentId: String) = CampsiteSummaryDto(
        id = contentId.hashCode().toLong(),
        contentId = contentId,
        name = "캠핑장 $contentId",
        sbrsKnown = true,
        facilities = listOf("전기"),
    )

    private fun onePage(vararg contentIds: String) = PageResponseDto(
        content = contentIds.map(::summaryDto),
        totalElements = contentIds.size.toLong(),
    )

    private fun detailEntity(contentId: String, cachedAt: Long) = CampsiteDetailEntity(
        contentId = contentId,
        name = "캠핑장 $contentId",
        lineIntro = null, intro = null, feature = null,
        doNm = null, sigunguNm = null, addr1 = null, addr2 = null,
        latitude = null, longitude = null,
        tel = null, homepage = null, resveUrl = null, firstImageUrl = null,
        petPolicy = null, brazierType = null, manageStatus = null,
        toiletCount = null, showerCount = null, washstandCount = null,
        facilities = emptyMap(),
        unknownCategories = emptyList(),
        cachedAt = cachedAt,
        lastAccessedAt = cachedAt,
    )

    private suspend fun seedCachedSearch(filter: CampsiteFilter, fetchedAt: Long) {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(page = onePage("A", "B"))
        repository.search(keyword = null, filter = filter, page = 0)
        // 저장 시각을 테스트가 아는 값으로 고정한다 (cachePage 는 현재 시각을 쓴다)
        cache.rewriteFetchedAt(fetchedAt)
    }

    // ── 12~14. 폴백의 기본 ──────────────────────────────────────────────────

    @Test
    fun `네트워크 성공이면 NETWORK 이고 캐시에 저장된다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(page = onePage("A", "B"))

        val result = repository.search(keyword = null, page = 0)

        assertEquals(Cached.Source.NETWORK, result.source)
        assertEquals(listOf("A", "B"), result.value.content.map { it.contentId })
        // 다음에 오프라인이 되면 이게 나와야 한다
        assertEquals(1, cache.observedQueryKeys.size)
    }

    @Test
    fun `오프라인에 캐시가 있으면 CACHE 이고 fetchedAt 은 저장 시각이다`() = runTest {
        seedCachedSearch(CampsiteFilter(), fetchedAt = 1_700_000_000_000L)
        api.behavior = FakeCampingFinderApi.Behavior.Offline

        val result = repository.search(keyword = null, page = 0)

        assertEquals(Cached.Source.CACHE, result.source)
        // ★ 읽은 시각을 넣으면 나이가 항상 0이 되어 배너가 "방금 받음" 이라고 거짓말한다
        assertEquals(1_700_000_000_000L, result.fetchedAt)
        assertEquals(listOf("A", "B"), result.value.content.map { it.contentId })
    }

    @Test
    fun `오프라인에 캐시도 없으면 원래 예외를 던진다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Offline

        val thrown = runCatching { repository.search(keyword = null, page = 0) }.exceptionOrNull()

        assertTrue("IOException 이 그대로 나와야 화면이 '연결 없음'을 말할 수 있다", thrown is IOException)
    }

    // ── 15. D3 ──────────────────────────────────────────────────────────────

    @Test
    fun `HTTP 오류는 캐시로 폴백하지 않고 던진다`() = runTest {
        seedCachedSearch(CampsiteFilter(), fetchedAt = 1_000L)
        api.behavior = FakeCampingFinderApi.Behavior.Http(code = 500)

        val thrown = runCatching { repository.search(keyword = null, page = 0) }.exceptionOrNull()

        // 캐시가 멀쩡히 있어도 덮지 않는다.
        // 잘못된 요청을 오래된 캐시로 가리면 그 버그는 영원히 안 보인다.
        assertTrue("HttpException 이어야 한다: $thrown", thrown is HttpException)
    }

    @Test
    fun `상세도 HTTP 오류는 캐시로 폴백하지 않는다`() = runTest {
        cache.seedDetail(detailEntity("A", cachedAt = 1_000L))
        api.behavior = FakeCampingFinderApi.Behavior.Http(code = 404)

        val thrown = runCatching { repository.detail("A") }.exceptionOrNull()

        assertTrue("HttpException 이어야 한다: $thrown", thrown is HttpException)
    }

    // ── 16. 캐시 쓰기 실패 ──────────────────────────────────────────────────

    @Test
    fun `캐시 쓰기가 실패해도 네트워크 결과는 그대로 나온다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(page = onePage("A"))
        cache.failWrites = true

        val result = repository.search(keyword = null, page = 0)

        // 저장공간이 찼다고 이미 손에 들어온 검색 결과를 못 보여주는 것은 말이 안 된다
        assertEquals(Cached.Source.NETWORK, result.source)
        assertEquals(listOf("A"), result.value.content.map { it.contentId })
    }

    // ── 17·18. 쿼리 키 ──────────────────────────────────────────────────────

    /**
     * ★ 집합의 순회 순서는 보장되지 않는다.
     *
     * <p>정렬하지 않으면 같은 조건이 다른 키가 되어 캐시가 조용히 빗나간다 —
     * "가끔 오프라인에서 결과가 안 나온다" 는, 재현되지 않아 영원히 못 고치는 버그가 이렇게 생긴다.
     */
    @Test
    fun `필터 집합의 순서가 달라도 같은 쿼리 키다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(page = onePage("A"))

        repository.search(null, CampsiteFilter(sbrs = setOf("전기", "온수", "무선인터넷")), page = 0)
        repository.search(null, CampsiteFilter(sbrs = setOf("무선인터넷", "전기", "온수")), page = 0)

        assertEquals(2, cache.observedQueryKeys.size)
        assertEquals(cache.observedQueryKeys[0], cache.observedQueryKeys[1])
    }

    @Test
    fun `induty 집합의 순서도 키를 바꾸지 않는다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(page = onePage("A"))

        repository.search(null, CampsiteFilter(induty = setOf("일반야영장", "글램핑")), page = 0)
        repository.search(null, CampsiteFilter(induty = setOf("글램핑", "일반야영장")), page = 0)

        assertEquals(cache.observedQueryKeys[0], cache.observedQueryKeys[1])
    }

    /** ★ 이 값 하나로 결과가 849곳 달라진다. 키에서 빠지면 두 검색이 서로를 덮어쓴다. */
    @Test
    fun `includeUnknown 이 다르면 쿼리 키가 다르다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(page = onePage("A"))

        repository.search(null, CampsiteFilter(includeUnknown = true), page = 0)
        repository.search(null, CampsiteFilter(includeUnknown = false), page = 0)

        assertNotEquals(cache.observedQueryKeys[0], cache.observedQueryKeys[1])
    }

    @Test
    fun `키워드가 다르면 쿼리 키가 다르다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(page = onePage("A"))

        repository.search("가평", page = 0)
        repository.search("철원", page = 0)

        assertNotEquals(cache.observedQueryKeys[0], cache.observedQueryKeys[1])
    }

    @Test
    fun `petAllowed 가 다르면 쿼리 키가 다르다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(page = onePage("A"))

        repository.search(null, CampsiteFilter(petAllowed = true), page = 0)
        repository.search(null, CampsiteFilter(petAllowed = false), page = 0)

        assertNotEquals(cache.observedQueryKeys[0], cache.observedQueryKeys[1])
    }

    // ── 상세 ────────────────────────────────────────────────────────────────

    /**
     * ★ 나이는 <b>받은</b> 시각이지 <b>연</b> 시각이 아니다.
     *
     * <p>둘을 반드시 다른 값으로 심는다. 같게 두면 `fetchedAt = lastAccessedAt` 로 바꿔도
     * 테스트가 통과한다 — 실제로 그렇게 만들어 놓고 변이 테스트에서 놓쳤다.
     * 그리고 이 상황(예전에 받아서 최근에 다시 연 캠핑장)이야말로 현장에서 가장 흔하다.
     */
    @Test
    fun `오프라인 상세는 받은 시각을 나이로 쓴다 — 연 시각이 아니다`() = runTest {
        val received = 1_700_000_000_000L
        val openedLater = received + 10L * 24 * 60 * 60 * 1000 // 열흘 뒤에 다시 열어 봤다
        cache.seedDetail(detailEntity("A", cachedAt = received).copy(lastAccessedAt = openedLater))
        api.behavior = FakeCampingFinderApi.Behavior.Offline

        val result = repository.detail("A")

        assertEquals(Cached.Source.CACHE, result.source)
        // 열흘 전에 연 것이지 열흘 전에 받은 것이 아니다. 나이는 받은 시각 기준이어야 한다.
        assertEquals(received, result.fetchedAt)
        // 열었다는 사실은 남되(LRU), 나이 계산에는 쓰이지 않는다
        assertTrue(
            "touchDetail 이 lastAccessedAt 을 올려야 한다",
            cache.detailSnapshot("A")!!.lastAccessedAt > openedLater,
        )
    }

    // ── ★ 캐시 읽기가 터져도 원본 예외를 가리지 않는다 ─────────────────────────

    /**
     * 캐시를 읽다 터지는 경우.
     *
     * <p>사용자가 알아야 하는 것은 <b>"네트워크가 안 된다"</b> 이지
     * {@code SerializationException} 이나 {@code SQLiteException} 이 아니다.
     * 캐시는 서버 응답의 파생본이라(D5), 읽히지 않는 캐시는 <b>캐시가 없는 것과 같다.</b>
     *
     * <p>정확히 신호가 없는 그 순간에 앱이 낯선 예외로 죽는 것이 이 기능이 막으려던 상황이다.
     */
    @Test
    fun `캐시 읽기가 실패하면 네트워크 예외가 그대로 나온다`() = runTest {
        seedCachedSearch(CampsiteFilter(), fetchedAt = 1_000L)
        api.behavior = FakeCampingFinderApi.Behavior.Offline
        cache.failReads = true

        val thrown = runCatching { repository.search(keyword = null, page = 0) }.exceptionOrNull()

        assertTrue("IOException 이어야 한다: $thrown", thrown is IOException)
        // 원인은 감추지 않는다 — 죽이지 않을 뿐 기록은 남긴다.
        assertTrue(
            "캐시 실패가 suppressed 로 남아 있어야 한다",
            thrown!!.suppressedAnywhere().any { it is IllegalStateException },
        )
    }

    /**
     * `suppressed` 를 예외 사슬 전체에서 찾는다.
     *
     * <p>⚠️ {@code thrown.suppressed} 만 보면 안 된다. 코루틴의 <b>스택트레이스 복구</b>가
     * {@code withContext} 경계를 넘을 때 예외를 <b>복사</b>하는데, 복사본은 suppressed 를 옮기지 않고
     * 원본을 {@code cause} 로 단다. 실측 결과:
     *
     * <pre>thrown=IOException suppressed=0 cause=IOException causeSuppressed=1</pre>
     *
     * <p>복구는 릴리스 빌드에서 꺼져 있을 수 있어 어느 쪽에 붙을지가 환경에 따라 달라진다.
     * 그래서 사슬 전체를 훑는다 — 검증하려는 것은 "어디에 붙었는가" 가 아니라
     * "기록이 남았는가" 다.
     */
    private fun Throwable.suppressedAnywhere(): List<Throwable> =
        generateSequence(this) { it.cause }
            .take(5)
            .flatMap { it.suppressed.asSequence() }
            .toList()

    @Test
    fun `상세 캐시 읽기가 실패해도 네트워크 예외가 그대로 나온다`() = runTest {
        cache.seedDetail(detailEntity("A", cachedAt = 1_000L))
        api.behavior = FakeCampingFinderApi.Behavior.Offline
        cache.failReads = true

        val thrown = runCatching { repository.detail("A") }.exceptionOrNull()

        assertTrue("IOException 이어야 한다: $thrown", thrown is IOException)
    }

    // ── 필터 항목 ───────────────────────────────────────────────────────────

    @Test
    fun `오프라인이면 저장해 둔 필터 항목을 쓴다`() = runTest {
        api.behavior = FakeCampingFinderApi.Behavior.Ok(
            filters = mapOf("SBRS" to listOf(FilterOptionDto("전기", "전기"))),
        )
        repository.filterOptions()

        api.behavior = FakeCampingFinderApi.Behavior.Offline
        val offline = repository.filterOptions()

        // 필터 시트가 비어 보이면 사용자는 앱이 고장 났다고 읽는다
        assertEquals(listOf("전기"), offline.sbrs.map { it.code })
    }

    /**
     * ★ 저장된 payload 의 구조가 바뀐 경우.
     *
     * <p>이 payload 는 <b>DB 스키마가 지켜 주지 않는다.</b> 컬럼은 그냥 TEXT 라
     * 앱을 업데이트해 {@code FilterOptionDto} 가 바뀌어도 Room 의 파괴적 마이그레이션이 발동하지 않는다.
     * 즉 <b>구버전이 쓴 JSON 을 신버전이 읽는 일이 실제로 생긴다.</b>
     */
    @Test
    fun `저장된 필터 payload 를 읽지 못해도 앱이 죽지 않는다`() = runTest {
        cache.seedFilterOptions(
            FilterOptionsEntity(payload = """{"SBRS":[{"code":"전기"}]}""", fetchedAt = 1_000L),
        )
        api.behavior = FakeCampingFinderApi.Behavior.Offline

        val thrown = runCatching { repository.filterOptions() }.exceptionOrNull()

        assertTrue(
            "직렬화 예외가 아니라 IOException 이 나와야 화면이 '연결 없음'을 말한다: $thrown",
            thrown is IOException,
        )
    }
}
