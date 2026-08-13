pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 모듈이 제멋대로 저장소를 추가하지 못하게 막는다. 의존성 출처를 한 곳에서만 관리한다.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 네이버 지도 SDK(com.naver.maps:map-sdk)는 Maven Central 에 없다. 전용 저장소를 쓴다.
        maven("https://repository.map.naver.com/archive/maven")
    }
}

rootProject.name = "camping-finder-android"
include(":app")
