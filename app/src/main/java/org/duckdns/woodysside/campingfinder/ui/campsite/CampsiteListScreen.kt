package org.duckdns.woodysside.campingfinder.ui.campsite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.duckdns.woodysside.campingfinder.R
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteSummaryDto
import org.duckdns.woodysside.campingfinder.ui.component.CacheBanner
import org.duckdns.woodysside.campingfinder.ui.component.CampsiteThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteListScreen(
    onCampsiteClick: (String) -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampsiteListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilter by remember { mutableStateOf(false) }

    if (showFilter) {
        CampsiteFilterSheet(
            current = state.filter,
            options = state.filterOptions,
            onApply = { filter ->
                viewModel.applyFilter(filter)
                showFilter = false
            },
            onDismiss = { showFilter = false },
        )
    }

    // 끝에서 3개 남았을 때 다음 페이지를 당긴다. 바닥에 닿고 나서 부르면 빈 화면이 보인다.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    // ★ 목록이 이미 있는 상태에서 추가 로딩이 실패하면 화면 어디에도 표시되지 않았다.
    //   사용자는 스크롤이 멈춘 이유를 알 수 없다. 스낵바로 알린다.
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (message != null && state.items.isNotEmpty()) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.campsite_list_title)) },
                actions = {
                    TextButton(onClick = { showFilter = true }) {
                        Text(
                            if (state.filter.isEmpty) {
                                stringResource(R.string.filter)
                            } else {
                                stringResource(R.string.filter_with_count, state.filter.activeCount)
                            },
                        )
                    }
                    TextButton(onClick = onOpenMap) { Text(stringResource(R.string.open_map)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // 검색창보다 위에 둔다. 목록을 스크롤해 내려가도 사라지지 않아야 한다 —
            // 사용자가 캠핑장 하나를 오래 들여다보는 순간이 바로 이 정보가 언제 것인지
            // 알아야 하는 순간이다.
            state.cacheNotice?.let { CacheBanner(it) }

            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
            )

            if (!state.isInitialLoading && state.errorMessage == null) {
                Text(
                    text = stringResource(R.string.result_count, state.totalElements),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            when {
                state.isInitialLoading -> Centered { CircularProgressIndicator() }

                state.errorMessage != null && state.items.isEmpty() -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.errorMessage!!, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                state.isEmptyResult -> Centered {
                    Text(
                        stringResource(R.string.empty_result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> PullToRefreshBox(
                    isRefreshing = state.isInitialLoading,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.contentId }) { campsite ->
                            CampsiteRow(
                                campsite = campsite,
                                onClick = { onCampsiteClick(campsite.contentId) },
                            )
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampsiteRow(
    campsite: CampsiteSummaryDto,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CampsiteThumbnail(
            imageUrl = campsite.firstImageUrl,
            modifier = Modifier.size(96.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = campsite.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(campsite.doNm, campsite.sigunguNm).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ★ 여기가 이 프로젝트의 핵심이 화면에 드러나는 지점이다.
            //
            // 시설 정보가 없는 곳(전체의 28.7%)을 빈칸으로 두면 사용자는 "시설이 없는 곳" 으로 읽는다.
            // 그건 데이터가 뒷받침하지 않는 주장이다. 모른다는 사실 자체를 표시한다.
            if (campsite.sbrsKnown) {
                Text(
                    text = campsite.facilities.take(4).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.facility_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            campsite.distanceKm?.let { km ->
                Text(
                    text = stringResource(R.string.distance_km, km),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}
