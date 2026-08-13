# R8 규칙 — M6(출시)에서 활성화했다.
#
# 전제: 대부분의 라이브러리는 자기 규칙을 아티팩트에 넣어 배포한다(consumer rules).
#   Retrofit 3 · OkHttp 5 · Coil 3 · Hilt · 네이버 map-sdk 가 그렇다.
# 그래서 아래 규칙은 "라이브러리가 안 챙겨주는 것"과 "챙겨주더라도 명시해 두는 게 나은 것"만 적는다.
#
# ⚠️ 이 파일이 맞는지는 빌드가 통과하는 것으로 증명되지 않는다.
#    반드시 릴리스 빌드를 실기기에 설치해 실제 API 호출까지 확인할 것.
#    R8 실패는 컴파일이 아니라 런타임에 나온다.


### 1. kotlinx.serialization ############################################
#
# 가장 잘 깨지는 지점이다. @Serializable 클래스의 동반 객체 serializer() 를
# 리플렉션으로 찾는 경로가 있어 이름이 바뀌면 런타임에 터진다.
#
# 증상: SerializationException: Serializer for class 'X' is not found
#       (디버그에서는 절대 재현되지 않는다)

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# @Serializable 이 붙은 클래스의 합성 serializer 를 보존한다.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# DTO 는 통째로 보존한다.
# 이 앱의 DTO 는 20여 개뿐이라 크기 이득보다 안전이 크다.
# 서버 응답 필드명과 1:1로 묶여 있어 난독화 이득도 사실상 없다.
-keep class org.duckdns.woodysside.campingfinder.data.remote.dto.** { *; }


### 2. Retrofit — 제네릭 시그니처 ########################################
#
# Retrofit 은 Call<PageResponseDto<CampsiteSummaryDto>> 의 타입 인자를
# 런타임에 읽어 컨버터를 고른다. Signature 속성이 지워지면 이 정보가 사라진다.
#
# 증상: IllegalArgumentException: Unable to create converter for ...

-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# API 인터페이스는 동적 프록시로 구현된다. 메서드 시그니처가 그대로 있어야 한다.
-keep interface org.duckdns.woodysside.campingfinder.data.remote.CampingFinderApi { *; }


### 3. 네이버 지도 ######################################################
#
# map-sdk 는 네이티브(JNI) 호출이 있고 스타일 파싱에 리플렉션을 쓴다.
# 지도가 안 그려지는 실패는 M3 에서 이미 두 번 당했다 — 원인 판별이 어려우므로
# R8 을 변수에서 아예 제거한다.

-keep class com.naver.maps.** { *; }
-dontwarn com.naver.maps.**


### 4. 스택트레이스 가독성 ###############################################
#
# 난독화된 크래시 리포트는 읽을 수 없다. 줄번호를 남기고 원본 파일명은 감춘다.
# mapping.txt 를 Play Console 에 업로드하면 콘솔이 자동으로 복원한다.
#   → app/build/outputs/mapping/release/mapping.txt

-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile


### 5. 코루틴 ###########################################################

-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}


### 6. 릴리스에서 로그 제거 #############################################
#
# android.util.Log 호출을 통째로 들어낸다. 본문 로깅 인터셉터는 이미
# BuildConfig.DEBUG 로 막혀 있지만(NetworkModule), 나머지 Log 호출은 남는다.
# assumenosideeffects 는 -dontoptimize 상태에서는 동작하지 않는다 — 최적화가 켜져 있어야 한다.

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
