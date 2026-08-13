package org.duckdns.woodysside.campingfinder.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 컬렉션 필드를 TEXT 한 칸에 담는다.
 *
 * <p>부대시설 목록을 별도 테이블로 정규화하지 않은 이유:
 * <b>앱은 이 값으로 검색하지 않는다.</b> 조합 필터는 서버가 계산하고(M5 문서 D1 참고)
 * 앱은 받은 결과를 보여줄 뿐이다. 조회 조건으로 쓰지 않는 값을 정규화하면
 * 조인만 늘고 얻는 것이 없다.
 *
 * <p>구분자로 문자열을 이어붙이지 않고 JSON 을 쓴다. 시설명에 콤마가 들어오는 순간
 * 구분자 방식은 조용히 깨진다 — M0 에서 원본 데이터가 정확히 그런 식으로 배신했다.
 */
object Converters {

    /**
     * 컨버터 전용 Json.
     *
     * <p>NetworkModule 의 Json 인스턴스를 재사용하지 않는다. 그쪽은 서버 응답을 견디기 위해
     * {@code ignoreUnknownKeys} 와 {@code explicitNulls = false} 가 켜져 있는데,
     * 여기는 우리가 쓰고 우리가 읽는 값이라 관대할 필요가 없다.
     * 읽다가 실패하면 그건 우리 버그이므로 조용히 넘기면 안 된다.
     */
    private val json = Json

    private val stringListSerializer = ListSerializer(String.serializer())
    private val stringListMapSerializer = MapSerializer(String.serializer(), stringListSerializer)

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(stringListSerializer, value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        json.decodeFromString(stringListSerializer, value)

    @TypeConverter
    fun stringListMapToJson(value: Map<String, List<String>>): String =
        json.encodeToString(stringListMapSerializer, value)

    @TypeConverter
    fun jsonToStringListMap(value: String): Map<String, List<String>> =
        json.decodeFromString(stringListMapSerializer, value)
}
