import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 업로드 키 정보. 커밋하지 않는다 (.gitignore 에 keystore.properties 가 있다).
// 파일이 없으면 서명 설정 없이 빌드한다 — 다른 사람이 클론해도 debug 빌드는 그대로 된다.
//
// ⚠️ 이 키를 잃으면 앱을 영원히 갱신할 수 없다. Play 앱 서명에 등록한 뒤에도
//    업로드 키는 본인이 보관해야 한다. 분실 시 Google 에 재설정 요청이 필요하고 며칠 걸린다.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

/**
 * versionCode — git 커밋 수에서 만든다.
 *
 * Play 는 이 정수 하나로 "더 새 빌드인가" 를 판단한다. 사용자에게 보이는 versionName 과는
 * 완전히 별개이고, **한 번 쓴 숫자는 영원히 재사용할 수 없으며 항상 커져야 한다.**
 *
 * 손으로 관리하면 올리는 것을 잊는다. 잊으면 빌드(약 2분)와 업로드를 마친 뒤에야
 * "버전 코드 N은 이미 사용되었습니다" 로 거부당하고 처음부터 다시 해야 한다.
 * 비공개 테스트 14일 동안 수정본을 여러 번 올리므로 반드시 한 번은 당한다.
 *
 * 커밋 수를 고른 이유 — **어느 빌드가 어느 커밋인지 역추적할 수 있다.**
 * 테스터가 "앱이 이상하다" 고 할 때 versionCode 로 정확한 커밋을 찾을 수 있다.
 * 타임스탬프 방식은 절대 줄지 않는 대신 같은 커밋을 두 번 빌드하면 다른 숫자가 나와
 * 그 추적이 불가능하다.
 *
 * ⚠️ 커밋 히스토리를 squash·rebase 로 줄이면 이 숫자도 줄어든다.
 *    이미 올린 것보다 작아지면 Play 가 업로드를 거부한다. 그때는 커밋을 더 쌓거나
 *    아래 수동 지정으로 넘긴다.
 *
 * 수동 지정(CI·git 없는 환경·히스토리 재작성 후):
 *
 *     .\gradlew.bat bundleRelease -PversionCode=42
 */
val versionCodeFromGit: Int = run {
    val override = (project.findProperty("versionCode") as String?)?.toIntOrNull()
    if (override != null) return@run override

    val counted = try {
        // providers.exec 를 쓴다. 설정 단계에서 ProcessBuilder 를 직접 돌리면
        // 구성 캐시(configuration cache)를 켜는 순간 깨진다.
        val exec = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            isIgnoreExitValue = true
        }
        if (exec.result.get().exitValue == 0) {
            exec.standardOutput.asText.get().trim().toIntOrNull()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    // ★ 조용히 1 로 떨어뜨리지 않는다.
    //   이미 쓴 것보다 작은 versionCode 로 빌드가 나가면 업로드 단계에서야 알게 되고,
    //   최악은 "왜 거부되는지" 를 한참 찾는 것이다. 여기서 시끄럽게 죽는 편이 싸다.
    counted ?: throw GradleException(
        "versionCode 를 만들 수 없다 - git 저장소가 아니거나 git 을 찾지 못했다.\n" +
            "  수동으로 지정: ./gradlew <task> -PversionCode=<정수>",
    )
}

android {
    namespace = "org.duckdns.woodysside.campingfinder"
    compileSdk = 37

    defaultConfig {
        // ⚠️ Play 스토어에 한 번 올리면 영원히 바꿀 수 없다. 바꾸려면 완전히 새 앱이 된다.
        //
        // 보유한 주소(woodys-side.duckdns.org)를 뒤집어 지었다.
        // 패키지명은 본인이 통제하는 것에서 따오는 게 관례다 — campingfinder.com 은 소유가 아니다.
        // 하이픈은 Java 패키지명에 쓸 수 없어 woodys-side → woodysside 로 붙였다.
        //
        // 이 값은 NCP 지도 SDK 인증 조건이기도 하다. 바꾸면 NCP 콘솔도 함께 고쳐야 한다.
        applicationId = "org.duckdns.woodysside.campingfinder"

        // minSdk 26: java.time 을 디슈가링 없이 쓸 수 있는 최소 버전.
        // 캠핑장 운영기간·정산일 같은 날짜 계산이 많아 이 경계가 실질적이다.
        minSdk = 26
        targetSdk = 37

        // git 커밋 수. 위 versionCodeFromGit 주석 참고.
        // -PversionCode=N 으로 덮어쓸 수 있다.
        versionCode = versionCodeFromGit

        // 사용자에게 보이는 문자열. Play 는 이 값으로 신·구를 판단하지 않으므로
        // 유일할 필요도, 커질 필요도 없다. 손으로 관리하는 게 맞다 —
        // 무엇이 바뀌었는지는 사람이 정하는 것이지 커밋 수가 정할 일이 아니다.
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")

                // Play 는 AAB 를 받아 자기가 다시 서명하지만, 업로드 검증은 v2 서명으로 한다.
                enableV1Signing = false
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // 운영 API 를 그대로 본다. 백엔드가 이미 인터넷에 떠 있으므로
            // 앱 개발할 때마다 로컬 서버를 띄울 이유가 없다.
            //
            // 로컬 백엔드를 보려면 아래 줄로 바꾼다. 에뮬레이터에서 호스트 PC 는 10.0.2.2 다
            // (localhost 는 에뮬레이터 자신을 가리킨다). 평문 HTTP 는 debug 매니페스트에서만 허용된다.
            //   buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("String", "API_BASE_URL", "\"https://woodys-side.duckdns.org/\"")
        }
        release {
            // M6 에서 켰다. keep 규칙은 app/proguard-rules.pro 에 있다.
            //
            // ⚠️ 빌드가 통과했다는 것은 아무 증거도 되지 않는다. R8 실패는 런타임에 나온다.
            //    릴리스 빌드를 실기기에 설치해 목록·상세·지도·필터까지 직접 확인할 것.
            isMinifyEnabled = true

            // 리소스 축소는 코드 축소가 켜져 있어야만 동작한다.
            // 어떤 리소스가 지워졌는지는 build/outputs/mapping/release/resources.txt 에 남는다.
            isShrinkResources = true

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://woodys-side.duckdns.org/\"")

            // 키가 없으면 서명하지 않은 채로 빌드된다 (설치는 안 되지만 컴파일 검증은 된다).
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9 내장 Kotlin 에서는 이 블록이 android {} 안에 들어간다.
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

ksp {
    // 스키마 JSON 을 저장소에 남긴다. 지금은 파괴적 마이그레이션이라 당장 쓰이지 않지만,
    // 즐겨찾기처럼 "버리면 안 되는" 테이블이 생기는 순간 실제 마이그레이션을 써야 하고
    // 그때 이전 버전 스키마가 없으면 마이그레이션을 검증할 방법이 없다.
    // 필요해진 다음에 남기기 시작하면 이미 늦는다.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 오프라인 캐시. 캠핑장은 산속에 있고 거기서 신호가 안 잡힌다 — 부가 기능이 아니라 도메인 요구사항이다.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.naver.map.compose)
    // 래퍼가 가져오는 3.21.0 을 덮어쓴다. 자세한 이유는 libs.versions.toml 참고.
    implementation(libs.naver.map.sdk)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // DAO 테스트는 실제 SQLite 위에서만 의미가 있다.
    // JVM 단위 테스트로는 쿼리가 검증되지 않는다 — ORDER BY 누락이나 NOT IN 서브쿼리의
    // 동작은 SQLite 가 실제로 돌아야 드러난다.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
