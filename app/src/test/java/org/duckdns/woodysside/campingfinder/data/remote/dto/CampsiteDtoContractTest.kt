package org.duckdns.woodysside.campingfinder.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서버 응답 계약 테스트.
 *
 * <p>픽스처는 지어낸 JSON 이 아니라 **운영 API(https://woodys-side.duckdns.org)에서 실제로 받은 응답**이다.
 * 기기나 에뮬레이터 없이, 서버가 주는 형태와 앱이 기대하는 형태가 어긋나지 않았는지 확인한다.
 *
 * <p>서버가 응답을 바꾸면 여기서 먼저 깨진다 — 앱을 켜서 빈 화면을 보고 알아채는 것보다 낫다.
 */
class CampsiteDtoContractTest {

    // 서버가 필드를 추가해도 앱이 죽지 않아야 한다.
    // 앱은 스토어 심사를 거쳐야 갱신되므로 구버전 앱이 신버전 응답을 견뎌야 한다.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "픽스처 없음: $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `목록 응답이 그대로 역직렬화된다`() {
        val page = json.decodeFromString<PageResponseDto<CampsiteSummaryDto>>(fixture("campsites_page.json"))

        assertTrue("content 가 비어 있으면 픽스처가 잘못된 것이다", page.content.isNotEmpty())
        assertTrue("전체 건수가 0이면 서버 상태를 의심해야 한다", page.totalElements > 0)

        val first = page.content.first()
        assertTrue(first.contentId.isNotBlank())
        assertTrue(first.name.isNotBlank())
    }

    @Test
    fun `sbrsKnown 이 응답에 존재한다 — 이 필드가 없으면 앱이 거짓말을 하게 된다`() {
        val page = json.decodeFromString<PageResponseDto<CampsiteSummaryDto>>(fixture("campsites_page.json"))

        // 기본값(false)으로 조용히 채워지는 것과 서버가 실제로 내려주는 것을 구분해야 한다.
        val raw = fixture("campsites_page.json")
        assertTrue("서버 응답에 sbrsKnown 이 없다", raw.contains("\"sbrsKnown\""))

        // 정보를 아는 곳은 시설 목록이 비어 있지 않아야 앞뒤가 맞는다.
        page.content.filter { it.sbrsKnown }.forEach {
            assertTrue("sbrsKnown=true 인데 시설이 비었다: ${it.name}", it.facilities.isNotEmpty())
        }
    }

    @Test
    fun `상세 응답이 그대로 역직렬화된다`() {
        val detail = json.decodeFromString<CampsiteDetailDto>(fixture("campsite_detail.json"))

        assertEquals("102032", detail.contentId)
        assertTrue(detail.name.isNotBlank())
        assertNotNull("카테고리별 시설 맵이 없다", detail.facilities)
    }

    @Test
    fun `상세 응답에 unknownCategories 가 있다 — 정보 없는 항목을 표시하는 근거`() {
        val raw = fixture("campsite_detail.json")
        assertTrue("서버 응답에 unknownCategories 가 없다", raw.contains("\"unknownCategories\""))

        val detail = json.decodeFromString<CampsiteDetailDto>(raw)
        // 알려진 카테고리와 모르는 카테고리가 겹치면 안 된다.
        detail.unknownCategories.forEach { unknown ->
            assertFalse(
                "$unknown 이 facilities 와 unknownCategories 양쪽에 있다",
                detail.facilities.containsKey(unknown),
            )
        }
    }

    @Test
    fun `null 이 섞인 필드가 있어도 깨지지 않는다`() {
        // 원본 공공데이터에 값이 없는 항목이 많다. lineIntro, homepage 등이 null 로 온다.
        val detail = json.decodeFromString<CampsiteDetailDto>(fixture("campsite_detail.json"))

        // 예외 없이 여기까지 왔으면 통과. 값 자체는 null 이어도 정상이다.
        assertTrue(detail.contentId.isNotBlank())
    }

    @Test
    fun `모르는 필드가 추가돼도 견딘다`() {
        val withExtra = fixture("campsite_detail.json")
            .replaceFirst("{", """{"someNewFieldServerAdded":"값",""")

        val detail = json.decodeFromString<CampsiteDetailDto>(withExtra)

        assertEquals("102032", detail.contentId)
    }
}
