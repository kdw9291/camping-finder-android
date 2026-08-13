# camping-finder-android

캠핑장 탐색 앱. Kotlin + Jetpack Compose.

> 기획 문서: `C:\workspace\etc\camping-finder-app.md`
> 백엔드: `../camping-finder-api`

## 현재 상태 — M5 완료 (M6 출시 준비 중)

운영 API(`https://woodys-side.duckdns.org`)에 붙어 **실제 캠핑장 2,955곳**을 보여준다.

| 영역 | 내용 |
|---|---|
| 목록 | 썸네일(Coil), 무한 스크롤, 로딩/에러/빈 상태 |
| 검색 | 키워드 검색 + 400ms 디바운스 |
| 상세 | 시설·연락처·소개 + **정보 없는 항목 명시** |
| 지도 | 네이버 지도 SDK, "이 지역에서 검색", 시설 조합 필터 UI |
| 오프라인 | Room 캐시 — 신호가 없어도 마지막 결과와 열어 본 상세가 보인다 |
| 내비게이션 | navigation-compose **타입 안전 라우트** (문자열 경로 아님) |
| DI | Hilt |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization |
| 테스트 | **36건** — 계약 6 · 리포지토리 16(JVM) · **DAO 14(실기기/에뮬레이터)** |

### 의사결정 기록

코드보다 이쪽이 이 프로젝트의 본체다.

- [M2 — 앱 화면 연동](docs/process/M2-앱-화면-연동.md) — 목록·검색·상세. 실패 3건
- [M3 — 지도와 필터](docs/process/M3-지도와-필터.md) — **실패 5건, 원인 네 겹**
- [M5 — 오프라인 캐시](docs/process/M5-오프라인-캐시.md) — 무엇을 캐시하지 **않을지** 정한 기록
- [M6 — 출시](docs/process/M6-출시.md) — 일정 역산·함정·실기기 체크리스트
- [스토어 제출 자료](docs/release/store-listing.md) · [개인정보처리방침](docs/release/privacy-policy.html)

### ★ 이 앱이 다른 캠핑 앱과 갈리는 지점

**시설 정보가 없는 캠핑장이 전체의 28.7%다.** 빈칸으로 두면 사용자는 "시설이 없는 곳"으로 읽는다.
그건 데이터가 뒷받침하지 않는 주장이다.

- 목록: 정보가 없으면 **"시설 정보 없음"** 을 표시한다 (빈칸으로 두지 않는다)
- 상세: **"정보가 없는 항목"** 절을 따로 두고 *"시설이 없다는 뜻이 아닙니다"* 를 명시한다
- 검색: `includeUnknown=true` 가 기본값 — 끄면 "전기 있는 곳" 에서 849곳이 조용히 사라진다

## 스택

| 항목 | 버전 |
|---|---|
| AGP | 9.3.1 |
| Kotlin | 2.2.10 (**AGP 내장**) |
| Gradle | 9.5.1 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| Compose BOM | 2026.06.01 |
| Hilt | 2.60.1 |
| Retrofit / OkHttp | 3.0.0 / 5.4.0 |

## 빌드

```powershell
.\gradlew.bat assembleDebug
```

SDK 위치는 `local.properties` 또는 `ANDROID_HOME` 환경변수로 잡는다. `local.properties` 는 커밋하지 않는다.

## 로컬 백엔드에 붙이기

디버그 빌드는 `http://10.0.2.2:8080/` 을 본다. **에뮬레이터에서 호스트 PC 의 주소가 10.0.2.2 다** —
`localhost` 는 에뮬레이터 자신을 가리키므로 연결되지 않는다.

실기기로 테스트하려면 PC 의 LAN IP 로 바꿔야 한다.

## 설계 메모

- **AGP 9 는 Kotlin 을 내장한다** — `org.jetbrains.kotlin.android` 플러그인을 적용하면 충돌한다.
  Compose·serialization 플러그인 버전은 AGP 가 물고 있는 KGP(2.2.10)에 맞춰야 한다
- **타입 안전 내비게이션** — 문자열 경로는 인자를 빠뜨려도 컴파일되고 실행 중에 터진다
- **`ACCESS_COARSE_LOCATION` 만 요구** — 반경 20km 검색에 미터 단위 정확도는 불필요하다.
  권한이 과할수록 심사와 사용자 거부율 양쪽에서 불리하다
- **평문 HTTP 는 디버그 빌드에만** — `src/debug/AndroidManifest.xml` 에서만 허용. release 는 HTTPS 전용
- **`ignoreUnknownKeys = true`** — 앱은 심사를 거쳐야 갱신된다. 구버전 앱이 신버전 응답을 견뎌야 한다

## 알려진 이슈 / TODO

| 항목 | 내용 |
|---|---|
| ⚠️ **R8 실기기 미검증** | M6 에서 켰다. 빌드 성공은 증거가 아니다 — 릴리스 빌드로 실제 API 호출까지 확인해야 한다. [체크리스트](docs/process/M6-출시.md#42-실기기-체크리스트--에뮬레이터-금지) |
| ⚠️ **`ORDER BY position` 은 테스트가 못 지킨다** | 실행 계획이 복합 PK 인덱스를 타서 지워도 결과가 같다. 지금 맞는 것은 PK 컬럼 순서 덕이지 우리가 요구해서가 아니다. [M5 실패 기록](docs/process/M5-오프라인-캐시.md#8--실패-기록) |
| ⚠️ 비행기 모드 실기기 미검증 | DAO·리포지토리는 테스트로 덮었지만 화면 전체 흐름은 아직. [체크리스트](docs/process/M5-오프라인-캐시.md#6-검증--비행기-모드-실기기) |
| ⚠️ 업로드 키 미생성 | `keystore.properties.example` 참고. **잃으면 앱을 영원히 갱신할 수 없다** |
| ⚠️ NCP 릴리스 지문 | Play 앱 서명은 Google 키로 재서명한다. 업로드 키 지문만 등록하면 스토어 앱에서 지도가 안 뜬다 |
| ⚠️ 위치 권한 | M6 에서 **일부러 뺐다**(미사용 권한). 현위치 기능을 넣을 때 매니페스트 주석을 풀고 데이터 보안 양식도 함께 고칠 것 |
| ⚠️ Coil 3.3.0 고정 | 최신은 3.5.0 이지만 kotlin-stdlib 2.4.0 을 끌어와 AGP 9 내장 컴파일러(2.2.10)가 못 읽는다. KGP 를 올리면 그때 최신으로 |
| ⚠️ Room 2.8.4 고정 | 같은 이유로 Room 3.0(`androidx.room3`)을 쓰지 않았다. [M5 D6](docs/process/M5-오프라인-캐시.md) |
| ⚠️ `android.disallowKotlinSourceSets=false` | KSP 와 AGP 9 내장 Kotlin 충돌 회피용. KSP 가 갱신되면 제거하고 재검증할 것 |
| JDK 25 경고 | `Kotlin does not yet support 25 JDK target` — JVM_24 로 폴백된다. 빌드는 정상 |
| 스크린샷 | Play 등록정보용 4장 미촬영. [목록](docs/release/store-listing.md#스크린샷-촬영-목록-권장-4장) |
