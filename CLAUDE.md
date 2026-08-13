# camping-finder-android

캠핑장 탐색 앱. Kotlin + Jetpack Compose. **Google Play 출시까지가 목표.**

백엔드: https://woodys-side.duckdns.org (별도 저장소 `../camping-finder-api`)

---

## 시작하기 전에 읽을 것

이 프로젝트는 **결과물보다 의사결정 기록이 목적**이다.

| 문서 | 언제 읽나 |
|---|---|
| `docs/process/M2-앱-화면-연동.md` | 화면·API 연동 |
| `docs/process/M3-지도와-필터.md` | 지도·필터 |
| `docs/process/M5-오프라인-캐시.md` | 캐시. **D1~D6 설계 결정은 협상 대상이 아니다** |
| `docs/process/M6-출시.md` | 스토어 등록 |
| `../../etc/HANDOFF.md` | 전체 맥락 요약 |

**각 문서의 "실패 기록" 절이 본체다.**

---

## ★ 이 앱의 존재 이유

> **"모름"을 화면에 드러낸다.**

시설 정보가 없는 캠핑장이 전체의 28.7%다. 빈칸으로 두면 사용자는 "시설이 없는 곳"으로 읽고,
그건 데이터가 뒷받침하지 않는 주장이다.

- 목록: `sbrsKnown == false` → **"시설 정보 없음"** (빈칸 아님)
- 상세: **"정보가 없는 항목"** 절 + *"시설이 없다는 뜻이 아닙니다"*
- 썸네일: 이미지 없으면 **"사진 없음"** (회색 네모만 두면 로딩 중인지 구분 안 된다)
- 필터: `includeUnknown` 기본 **켬**. 끄면 "전기 있는 곳"이 2,846 → 1,997
- 지도: 마커가 상한에 걸리면 **"N곳 중 M곳만 표시"**

**새 화면을 만들 때도 이 원칙을 지켜라.** 불완전한 것을 감추지 않는다.

---

## 반드시 알아야 할 함정

| 함정 | 내용 |
|---|---|
| **AGP 9 는 Kotlin 내장** | KGP 2.2.10. `org.jetbrains.kotlin.android` 플러그인 적용 금지 |
| **라이브러리 버전 상한** | 내장 컴파일러가 메타데이터 2.3.0 까지만 읽는다. **새 의존성은 stdlib 요구 버전을 먼저 확인**하고, 넘으면 최신 대신 제약을 만족하는 최고 버전으로. Coil 3.5.0(stdlib 2.4.0)에서 당했다 → 3.3.0 사용 |
| **네이버 지도 키** | `CLIENT_ID` 아니고 **`NCP_KEY_ID`**. 콘솔이 "Client ID"라 불러도 그렇다 |
| **map-sdk 버전** | **3.23.3 명시.** 래퍼가 가져오는 3.21.0 은 최신 스타일 파싱 실패(`ParseStyle: mesh-batch`) → 타일이 안 그려진다 |
| **지도 = 빈 화면 + 마커** | 원인이 둘이고 증상이 같다. **로그로 구분**: `401 Unauthorized client`(인증) vs `ParseStyle`(SDK 버전) |
| **지도는 에뮬레이터로 검증 금지** | 그래픽이 무너져 앱 전체가 검은 화면이 된다. UI 계층은 멀쩡한데 화면만 안 그려진다. **실기기를 쓴다** |
| **지도 진입 시 자동 검색 금지** | 표면 생성 중 마커 100개를 올리면 ANR |
| **`joinToString`은 inline 아님** | 람다 안에서 `@Composable` 호출 불가. `map` 으로 먼저 변환 |
| **R8** | 규칙이 맞는지는 빌드 통과로 증명되지 않는다. **릴리스 빌드를 실기기에 설치해 실제 API 호출까지** 확인 |
| **`applicationId`** | `org.duckdns.woodysside.campingfinder`. **Play 업로드 후 변경 불가.** NCP 인증 조건이기도 하다 |

---

## 명령

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

실기기 확인:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb logcat -d | Select-String -Pattern 'NaverMap|FATAL|ParseStyle'
```

---

## 스택

Kotlin 2.2.10(AGP 내장) · Compose BOM 2026.06.01 · Hilt 2.60.1 · Retrofit 3 · OkHttp 5 ·
Coil 3.3.0 · Room 2.8.4 · naver-map-compose 1.9.0 + map-sdk 3.23.3 · compileSdk 37 · minSdk 26

## 하지 말 것

- Room 3.0(`androidx.room3`)으로 올리기 (M5 D6)
- 오프라인 필터 검색 구현 (M5 D1 — 필터 로직을 SQLite 에 복제하지 않는다)
- 지도 결과 캐싱 (M5 D4 — 타일 없이 마커만 있으면 쓸모없다)
- 서명 키(`*.jks`)·`local.properties` 커밋
