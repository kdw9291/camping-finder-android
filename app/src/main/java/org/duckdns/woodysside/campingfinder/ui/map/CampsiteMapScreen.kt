package org.duckdns.woodysside.campingfinder.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.compose.rememberMarkerState
import org.duckdns.woodysside.campingfinder.R

/** 서울 시청. 첫 진입 기준점. */
private val DEFAULT_CENTER = LatLng(37.5665, 126.9780)
private const val DEFAULT_ZOOM = 10.0

@OptIn(ExperimentalMaterial3Api::class, ExperimentalNaverMapApi::class)
@Composable
fun CampsiteMapScreen(
    onCampsiteClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampsiteMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(DEFAULT_CENTER, DEFAULT_ZOOM)
    }

    // ★ 지도 인증 실패를 화면에 드러낸다.
    //
    // 리스너를 등록하지 않으면 SDK 는 조용히 실패한다 — 타일만 안 나오고
    // 마커는 그대로 그려져서, 지도가 빈 화면인 이유를 알 방법이 없다.
    // (실제로 이 화면을 처음 띄웠을 때 그 상태였다.)
    val context = LocalContext.current
    var authError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val sdk = NaverMapSdk.getInstance(context)
        sdk.setOnAuthFailedListener { exception ->
            authError = "[${exception.errorCode}] ${exception.message.orEmpty()}"
        }
        onDispose { sdk.setOnAuthFailedListener(null) }
    }

    // 진입 직후에는 검색하지 않는다.
    //
    // 지도 표면이 만들어지는 동안 마커 100개를 동시에 올리면 메인 스레드가 묶여
    // ANR("Input dispatching timed out")이 난다. 실제로 그렇게 됐다.
    // 지도가 뜬 뒤 사용자가 "이 지역에서 검색"을 누르게 한다.

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {

            NaverMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(),
                uiSettings = MapUiSettings(isZoomControlEnabled = false),
            ) {
                state.markers.forEach { campsite ->
                    // 좌표가 없는 캠핑장은 서버가 반경 검색에서 제외하지만, 방어적으로 한 번 더 거른다.
                    val lat = campsite.latitude
                    val lng = campsite.longitude
                    if (lat == null || lng == null) return@forEach

                    // ★ key() 로 감싼다.
                    //
                    // 이걸 빼면 조건부로 rememberMarkerState 를 부르게 되어 재구성마다
                    // remember 호출 구조가 달라진다. Compose 슬롯이 어긋나 재구성이 반복되고
                    // 메인 스레드가 묶인다.
                    key(campsite.contentId) {
                        Marker(
                            state = rememberMarkerState(position = LatLng(lat, lng)),
                            onClick = {
                                onCampsiteClick(campsite.contentId)
                                true
                            },
                        )
                    }
                }
            }

            // 이 지역 재검색. 지도를 옮긴 뒤 자동으로 다시 부르면 요청이 폭주하므로 사용자가 누르게 한다.
            Button(
                onClick = {
                    val target = cameraPositionState.position.target
                    viewModel.searchArea(
                        target.latitude,
                        target.longitude,
                        cameraPositionState.position.zoom,
                    )
                },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.search_this_area))
            }

            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            // 인증이 실패하면 지도가 빈 화면이 된다. 이유를 말해준다.
            authError?.let { message ->
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = stringResource(R.string.map_auth_failed, message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // ★ 마커 상한에 걸려 일부만 보이는 상황을 감추지 않는다.
            //   "이게 전부" 라고 오해하면 사용자가 잘못된 판단을 한다.
            if (state.truncated && !state.isLoading) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = stringResource(
                            R.string.map_truncated,
                            state.markers.size,
                            state.totalInArea,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
