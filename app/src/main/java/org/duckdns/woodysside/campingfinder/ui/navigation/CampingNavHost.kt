package org.duckdns.woodysside.campingfinder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.duckdns.woodysside.campingfinder.ui.campsite.CampsiteDetailScreen
import org.duckdns.woodysside.campingfinder.ui.campsite.CampsiteListScreen
import org.duckdns.woodysside.campingfinder.ui.map.CampsiteMapScreen
import kotlinx.serialization.Serializable

/**
 * 타입 안전 내비게이션. 경로를 문자열로 조립하지 않는다.
 * 문자열 경로는 인자를 빠뜨려도 컴파일이 되고 실행 중에야 터진다.
 */
@Serializable
data object CampsiteList

@Serializable
data class CampsiteDetail(val contentId: String)

@Serializable
data object CampsiteMap

@Composable
fun CampingNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = CampsiteList,
    ) {
        composable<CampsiteList> {
            CampsiteListScreen(
                onCampsiteClick = { contentId ->
                    navController.navigate(CampsiteDetail(contentId))
                },
                onOpenMap = { navController.navigate(CampsiteMap) },
            )
        }

        composable<CampsiteMap> {
            CampsiteMapScreen(
                onCampsiteClick = { contentId ->
                    navController.navigate(CampsiteDetail(contentId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<CampsiteDetail> {
            // contentId 는 ViewModel 이 SavedStateHandle.toRoute() 로 직접 꺼낸다.
            // 화면을 거쳐 넘기지 않아야 화면 재생성 시에도 안전하다.
            CampsiteDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
