package org.duckdns.woodysside.campingfinder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CampsiteSummaryEntity::class,
        SearchResultEntity::class,
        CampsiteDetailEntity::class,
        FilterOptionsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CampingFinderDatabase : RoomDatabase() {
    abstract fun campsiteCacheDao(): CampsiteCacheDao

    companion object {
        const val NAME = "camping-finder-cache.db"

        /**
         * 상세 캐시 상한.
         *
         * <p>200건 × 약 2KB ≈ 400KB. 캠핑 한 번 준비하며 훑어보는 후보가 수십 곳이므로
         * 여러 번의 여행 계획이 남는 크기다.
         *
         * <p><b>3,082건 전부를 미리 받지 않는다.</b> 기술적으로는 몇 MB 면 되고 그러면
         * 오프라인이 완벽해지지만, 사용자가 요청하지 않은 데이터를 셀룰러로 내려받는 일이다.
         * 캠핑장을 찾아보려고 앱을 연 사람에게 데이터 요금을 물리는 것은 앱이 할 일이 아니다.
         */
        const val MAX_CACHED_DETAILS = 200

        /**
         * 검색 결과 보관 기간.
         *
         * <p>⚠️ <b>TTL 이 아니다.</b> 만료됐다고 안 보여주는 것이 아니라, 30일이 지난 것을
         * 청소할 뿐이다. 그 사이에는 아무리 오래됐어도 그대로 꺼내 쓴다 — 나이를 함께 말하면서.
         *
         * <p>만료된 캐시를 감추는 구현은 산속에서 앱을 빈 화면으로 만든다.
         * 5일 전 정보는 정보가 없는 것보다 낫다. 5일 전 것이라고 말해 주기만 하면 된다.
         * 판단은 사용자가 한다. 이 앱이 부대시설의 빈 값을 감추지 않는 것과 같은 이유다.
         *
         * <p>상세는 이 청소에 포함되지 않는다. 상세는 나이가 아니라 LRU 로만 정리한다
         * (다음 주에 갈 캠핑장은 오래전에 받았을 수 있다).
         */
        const val SEARCH_RESULT_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
