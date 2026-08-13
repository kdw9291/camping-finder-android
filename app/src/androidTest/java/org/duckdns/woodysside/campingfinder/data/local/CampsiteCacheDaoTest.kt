package org.duckdns.woodysside.campingfinder.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO 검증.
 *
 * <p><b>M5 문서 5번 "함정" 표가 곧 이 테스트 목록이다.</b> 각 테스트는 그 함정에 빠졌을 때
 * 빨개지도록 썼다 — 통과만 하는 테스트는 아무것도 지키지 않는다
 * (백엔드 M7 에서 정확히 그 일이 있었다: `원본_보관` 테스트가 통과하면서 46개 필드 유실을 놓쳤다).
 *
 * <p>인메모리 DB 를 쓴다. 파일 DB 는 테스트 간 상태가 새고, 한 번 깨지면
 * 그 다음부터 무엇이 원인인지 알 수 없게 된다.
 *
 * <p>실제 SQLite 위에서 돌려야 하는 이유: {@code ORDER BY} 누락이나 {@code NOT IN} 서브쿼리의
 * 동작은 쿼리를 실제로 실행해야 드러난다. JVM 에서 DAO 를 페이크로 두면 <b>쿼리 자체가 검증되지 않는다.</b>
 */
@RunWith(AndroidJUnit4::class)
class CampsiteCacheDaoTest {

    private lateinit var db: CampingFinderDatabase
    private lateinit var dao: CampsiteCacheDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CampingFinderDatabase::class.java,
        ).build()
        dao = db.campsiteCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private fun summary(
        contentId: String,
        name: String = "캠핑장 $contentId",
        facilities: List<String> = listOf("전기"),
        cachedAt: Long = 1_000L,
    ) = CampsiteSummaryEntity(
        contentId = contentId,
        id = contentId.hashCode().toLong(),
        name = name,
        doNm = "강원특별자치도",
        sigunguNm = "철원군",
        addr1 = "어딘가",
        latitude = 38.22,
        longitude = 127.18,
        firstImageUrl = null,
        petPolicy = "ALLOWED",
        manageStatus = "OPERATING",
        sbrsKnown = true,
        facilities = facilities,
        cachedAt = cachedAt,
    )

    private fun detail(
        contentId: String,
        cachedAt: Long,
        lastAccessedAt: Long = cachedAt,
        facilities: Map<String, List<String>> = mapOf("SBRS" to listOf("전기")),
        unknownCategories: List<String> = listOf("THEMA_ENVRN"),
    ) = CampsiteDetailEntity(
        contentId = contentId,
        name = "캠핑장 $contentId",
        lineIntro = null,
        intro = null,
        feature = null,
        doNm = "강원특별자치도",
        sigunguNm = "철원군",
        addr1 = "어딘가",
        addr2 = null,
        latitude = 38.22,
        longitude = 127.18,
        tel = "033-000-0000",
        homepage = null,
        resveUrl = null,
        firstImageUrl = null,
        petPolicy = "ALLOWED",
        brazierType = "INDIVIDUAL",
        manageStatus = "OPERATING",
        toiletCount = 3,
        showerCount = 2,
        washstandCount = 1,
        facilities = facilities,
        unknownCategories = unknownCategories,
        cachedAt = cachedAt,
        lastAccessedAt = lastAccessedAt,
    )

    // ── 1. 순서 ─────────────────────────────────────────────────────────────

    /**
     * ★ `ORDER BY r.position` 이 빠지면 실패해야 한다.
     *
     * <p>그냥 한 번 저장하고 읽으면 <b>이 테스트는 ORDER BY 없이도 통과한다</b> —
     * campsite_summary 의 rowid 순서가 곧 저장 순서라 우연히 맞아떨어진다.
     * 그래서 <b>먼저 다른 순서로 요약을 심어 두고</b> rowid 순서와 position 순서를 어긋나게 만든다.
     * 같은 캠핑장이 여러 검색에 걸리는 것은 실제로 늘 일어나는 일이다.
     */
    @Test
    fun 저장한_순서_그대로_돌려준다() = runTest {
        // rowid 순서를 A, B, C 로 먼저 굳힌다 (다른 검색이 지나간 상황)
        dao.replacePage("other", 0, listOf(summary("A"), summary("B"), summary("C")), 3, 1_000L)

        // 이번 검색의 순서는 C, A, B — rowid 순서와 다르다
        dao.replacePage("q1", 0, listOf(summary("C"), summary("A"), summary("B")), 3, 2_000L)

        assertEquals(
            listOf("C", "A", "B"),
            dao.summariesForPage("q1", 0).map { it.contentId },
        )
    }

    /**
     * ★ 행이 저장된 물리적 순서와 `position` 이 어긋난 상태.
     *
     * <p>위 테스트만으로는 `ORDER BY r.position` 을 지워도 통과한다 — 실측으로 확인했다.
     * `replacePage` 가 position 순서대로 넣으니 삽입 순서와 position 순서가 같고,
     * SQLite 가 어느 계획을 고르든 우연히 맞는다.
     *
     * <p>여기서는 <b>일부러 뒤섞어</b> 넣는다. 행의 물리적 순서는 우리가 통제하는 값이 아니고,
     * SQL 은 `ORDER BY` 없이 어떤 순서도 약속하지 않는다.
     */
    @Test
    fun 행_순서가_뒤섞여_있어도_position_순서로_돌려준다() = runTest {
        dao.upsertSummaries(listOf(summary("A"), summary("B"), summary("C")))
        // position 2, 0, 1 순서로 삽입한다 (삽입 순서 ≠ position 순서)
        dao.upsertSearchResults(
            listOf(
                SearchResultEntity("q1", 0, 2, "B", 3, 1_000L),
                SearchResultEntity("q1", 0, 0, "C", 3, 1_000L),
                SearchResultEntity("q1", 0, 1, "A", 3, 1_000L),
            ),
        )

        assertEquals(
            listOf("C", "A", "B"),
            dao.summariesForPage("q1", 0).map { it.contentId },
        )
    }

    // ── 2·3·4. 페이지 교체와 격리 ────────────────────────────────────────────

    @Test
    fun 같은_페이지를_두_번_저장해도_누적되지_않는다() = runTest {
        val page = listOf(summary("A"), summary("B"))
        dao.replacePage("q1", 0, page, 2, 1_000L)
        dao.replacePage("q1", 0, page, 2, 2_000L)

        assertEquals(2, dao.summariesForPage("q1", 0).size)
        // 교체됐으므로 나중 값이 남는다
        assertEquals(2_000L, dao.fetchedAtForPage("q1", 0))
    }

    @Test
    fun 다른_검색의_결과가_섞이지_않는다() = runTest {
        dao.replacePage("q1", 0, listOf(summary("A")), 1, 1_000L)
        dao.replacePage("q2", 0, listOf(summary("B")), 1, 1_000L)

        assertEquals(listOf("A"), dao.summariesForPage("q1", 0).map { it.contentId })
        assertEquals(listOf("B"), dao.summariesForPage("q2", 0).map { it.contentId })
    }

    /** 요약과 순서를 분리한 설계가 실제로 동작하는지. 중복 저장되면 캐시가 검색 수만큼 부푼다. */
    @Test
    fun 같은_캠핑장이_두_검색에_걸려도_요약은_한_행이다() = runTest {
        dao.replacePage("q1", 0, listOf(summary("A"), summary("B")), 2, 1_000L)
        dao.replacePage("q2", 0, listOf(summary("A")), 1, 1_000L)

        val rows = db.query("SELECT COUNT(*) FROM campsite_summary WHERE contentId = 'A'", null)
        rows.use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    // ── 5. 고아 청소 ────────────────────────────────────────────────────────

    /**
     * 고아 요약만 지운다.
     *
     * <p>상세로 저장된 요약까지 지우면 오프라인에서 상세 화면이 깨진다 —
     * 정확히 신호가 없는 현장에서 필요한 것이 그것이다.
     */
    @Test
    fun 고아_요약만_지운다() = runTest {
        dao.replacePage("q1", 0, listOf(summary("KEEP")), 1, 1_000L)
        dao.upsertSummaries(listOf(summary("ORPHAN"), summary("HAS_DETAIL")))
        dao.upsertDetail(detail("HAS_DETAIL", cachedAt = 1_000L))

        dao.deleteOrphanSummaries()

        val remaining = allSummaryIds()
        assertEquals(listOf("HAS_DETAIL", "KEEP"), remaining.sorted())
    }

    // ── 6·7. LRU ────────────────────────────────────────────────────────────

    /**
     * ★ 기준이 `lastAccessedAt` 이지 `cachedAt` 이 아니다.
     *
     * <p>픽스처를 일부러 어긋나게 만들었다 — 가장 최근에 <b>받은</b> 것이
     * 가장 최근에 <b>연</b> 것과 반대 순서다. `cachedAt DESC` 로 바꾸면 정확히 뒤집힌 답이 나온다.
     */
    @Test
    fun trimDetails_는_마지막으로_연_순서로_남긴다() = runTest {
        // cachedAt 은 OLD 가 가장 오래됐지만, 열어 본 것은 OLD 가 가장 최근이다
        dao.upsertDetail(detail("OLD_BUT_OPENED", cachedAt = 100L, lastAccessedAt = 9_000L))
        dao.upsertDetail(detail("MID", cachedAt = 200L, lastAccessedAt = 8_000L))
        dao.upsertDetail(detail("NEW_BUT_IGNORED", cachedAt = 9_999L, lastAccessedAt = 7_000L))
        dao.upsertDetail(detail("STALE", cachedAt = 300L, lastAccessedAt = 1_000L))

        dao.trimDetails(3)

        // 다음 주에 갈 캠핑장은 오래전에 받았더라도 계속 열어 본 곳이다
        assertNotNull(dao.detail("OLD_BUT_OPENED"))
        assertNotNull(dao.detail("MID"))
        assertNotNull(dao.detail("NEW_BUT_IGNORED"))
        assertNull(dao.detail("STALE"))
    }

    /**
     * ★ `touchDetail` 이 `cachedAt` 을 건드리면 나이 표시가 거짓말을 한다.
     *
     * <p>배너가 "방금 받은 것" 이라고 말하는데 실제로는 3주 전 운영시간인 상황.
     * 이 앱이 통째로 반대하는 상태다.
     */
    @Test
    fun touchDetail_은_받은_시각을_바꾸지_않는다() = runTest {
        dao.upsertDetail(detail("A", cachedAt = 1_000L, lastAccessedAt = 1_000L))

        dao.touchDetail("A", 5_000L)

        val row = requireNotNull(dao.detail("A"))
        assertEquals("받은 시각이 바뀌면 배너가 거짓말을 한다", 1_000L, row.cachedAt)
        assertEquals(5_000L, row.lastAccessedAt)
    }

    // ── 8·9. 컨버터 ─────────────────────────────────────────────────────────

    /**
     * ★ 시설명에 콤마가 들어 있어도 보존된다.
     *
     * <p>구분자 이어붙이기로 회귀하면 여기서 깨진다. M0 에서 원본 데이터가 정확히 그렇게 배신했다 —
     * `마트.편의점`, `강/물놀이` 처럼 값 안에 구분자로 쓸 법한 문자가 들어 있다.
     */
    @Test
    fun 콤마가_든_시설명이_왕복해도_보존된다() = runTest {
        val tricky = listOf("마트.편의점", "강/물놀이", "계곡 물놀이", "a,b", "따옴표\"포함")
        dao.upsertSummaries(listOf(summary("A", facilities = tricky)))

        val loaded = requireNotNull(dao.summariesForPageOrNull("A"))
        assertEquals(tricky, loaded.facilities)
    }

    @Test
    fun 빈_리스트와_빈_맵이_왕복해도_보존된다() = runTest {
        dao.upsertSummaries(listOf(summary("A", facilities = emptyList())))
        dao.upsertDetail(
            detail("A", cachedAt = 1_000L, facilities = emptyMap(), unknownCategories = emptyList()),
        )

        assertEquals(emptyList<String>(), requireNotNull(dao.summariesForPageOrNull("A")).facilities)
        val d = requireNotNull(dao.detail("A"))
        assertEquals(emptyMap<String, List<String>>(), d.facilities)
        assertEquals(emptyList<String>(), d.unknownCategories)
    }

    @Test
    fun 중첩된_맵이_왕복해도_보존된다() = runTest {
        val facilities = mapOf(
            "SBRS" to listOf("전기", "온수", "마트.편의점"),
            "LCT" to listOf("강/물놀이"),
            "OPER_DE" to emptyList(),
        )
        dao.upsertDetail(detail("A", cachedAt = 1_000L, facilities = facilities))

        assertEquals(facilities, requireNotNull(dao.detail("A")).facilities)
    }

    // ── 10. 싱글턴 ──────────────────────────────────────────────────────────

    @Test
    fun 필터_항목은_여러_번_넣어도_한_행이다() = runTest {
        dao.upsertFilterOptions(FilterOptionsEntity(payload = "{\"a\":1}", fetchedAt = 1_000L))
        dao.upsertFilterOptions(FilterOptionsEntity(payload = "{\"b\":2}", fetchedAt = 2_000L))

        val count = db.query("SELECT COUNT(*) FROM filter_options", null)
        count.use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        assertEquals("{\"b\":2}", requireNotNull(dao.filterOptions()).payload)
    }

    // ── 11. 나이 청소 ───────────────────────────────────────────────────────

    /**
     * ★ D2 — 상세는 나이로 지우지 않는다.
     *
     * <p>상세야말로 신호 없는 현장에서 열리는 화면이다. 30일이 지났다고 지우면
     * 정확히 필요한 순간에 빈 화면이 된다. 상세는 LRU 로만 정리한다.
     */
    @Test
    fun 오래된_검색결과를_지워도_상세는_남는다() = runTest {
        dao.replacePage("q1", 0, listOf(summary("A")), 1, fetchedAt = 100L)
        dao.upsertDetail(detail("A", cachedAt = 100L))

        dao.deleteSearchResultsBefore(before = 1_000L)

        assertEquals(emptyList<String>(), dao.summariesForPage("q1", 0).map { it.contentId })
        assertNotNull("상세는 나이로 지우지 않는다 (D2)", dao.detail("A"))
    }

    @Test
    fun 아직_안_지난_검색결과는_남는다() = runTest {
        dao.replacePage("q1", 0, listOf(summary("A")), 1, fetchedAt = 5_000L)

        dao.deleteSearchResultsBefore(before = 1_000L)

        assertEquals(listOf("A"), dao.summariesForPage("q1", 0).map { it.contentId })
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun allSummaryIds(): List<String> {
        val ids = mutableListOf<String>()
        db.query("SELECT contentId FROM campsite_summary", null).use {
            while (it.moveToNext()) ids.add(it.getString(0))
        }
        return ids
    }

    /** 검색에 걸지 않고 요약 한 건만 꺼낸다. 컨버터 왕복 검증용. */
    private suspend fun CampsiteCacheDao.summariesForPageOrNull(contentId: String):
        CampsiteSummaryEntity? {
        upsertSearchResults(
            listOf(
                SearchResultEntity(
                    queryKey = "__probe__",
                    page = 0,
                    position = 0,
                    contentId = contentId,
                    totalElements = 1,
                    fetchedAt = 0L,
                ),
            ),
        )
        return summariesForPage("__probe__", 0).firstOrNull()
    }
}
