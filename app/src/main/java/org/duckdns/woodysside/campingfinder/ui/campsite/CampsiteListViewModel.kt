package org.duckdns.woodysside.campingfinder.ui.campsite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.duckdns.woodysside.campingfinder.data.CampsiteRepository
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampsiteListUiState(
    val items: List<CampsiteSummaryDto> = emptyList(),
    val keyword: String = "",
    val totalElements: Long = 0,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val endReached: Boolean = false,
    val filter: CampsiteFilter = CampsiteFilter(),
    val filterOptions: FilterOptions = FilterOptions(),

    /** 저장해 둔 결과를 보여주고 있으면 채워진다. null 이면 방금 서버에서 받은 것. */
    val cacheNotice: CacheNotice? = null,
) {
    val isEmptyResult: Boolean
        get() = !isInitialLoading && errorMessage == null && items.isEmpty()
}

/**
 * 목록 화면 상태.
 *
 * <p><b>Paging 3 를 쓰지 않았다.</b> 서버가 단순 오프셋 페이지 API 이고 전체가 2,955건이라
 * Paging 이 주는 이점(대용량 스트리밍, DB 캐시 연동)이 실현되지 않는다.
 * 직접 구현하면 로딩·에러·끝 도달 상태가 한눈에 보이고 테스트도 쉽다.
 * 필요해지면 그때 바꾼다.
 */
@HiltViewModel
class CampsiteListViewModel @Inject constructor(
    private val repository: CampsiteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampsiteListUiState())
    val uiState: StateFlow<CampsiteListUiState> = _uiState.asStateFlow()

    private var nextPage = 0
    private var searchJob: Job? = null

    init {
        refresh()
        loadFilterOptions()
    }

    /**
     * 필터 항목을 서버에서 받는다. 앱에 하드코딩하지 않는다 —
     * 시설 항목은 공공데이터가 늘리면 늘어나는데, 앱에 박아두면
     * 항목 하나 늘 때마다 스토어 심사를 다시 받아야 한다.
     */
    private fun loadFilterOptions() {
        viewModelScope.launch {
            runCatching { repository.filterOptions() }
                .onSuccess { options -> _uiState.update { it.copy(filterOptions = options) } }
                .onFailure { /* 필터 목록 실패는 검색을 막지 않는다. 필터만 비어 보인다. */ }
        }
    }

    fun applyFilter(filter: CampsiteFilter) {
        _uiState.update { it.copy(filter = filter) }
        refresh()
    }

    fun onKeywordChange(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }

        // 타자를 칠 때마다 요청하지 않는다. 마지막 입력 후 400ms 만 기다렸다 보낸다.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            refresh()
        }
    }

    fun refresh() {
        searchJob?.cancel()
        nextPage = 0
        _uiState.update { it.copy(isInitialLoading = true, errorMessage = null, endReached = false) }

        viewModelScope.launch {
            runCatching {
                repository.search(_uiState.value.keyword, _uiState.value.filter, page = 0)
            }
                .onSuccess { cached ->
                    val response = cached.value
                    nextPage = 1
                    _uiState.update {
                        it.copy(
                            items = response.content,
                            totalElements = response.totalElements,
                            isInitialLoading = false,
                            endReached = response.content.size < CampsiteRepository.PAGE_SIZE,
                            cacheNotice = CacheNotice.from(cached),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isInitialLoading = false, errorMessage = e.toUserMessage())
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isInitialLoading || state.isLoadingMore || state.endReached) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.search(state.keyword, state.filter, page = nextPage) }
                .onSuccess { cached ->
                    val response = cached.value
                    nextPage++
                    _uiState.update {
                        it.copy(
                            items = it.items + response.content,
                            isLoadingMore = false,
                            endReached = response.content.size < CampsiteRepository.PAGE_SIZE,
                            // 다음 페이지가 캐시에서 왔다면 목록 전체가 더는 최신이 아니다.
                            // 첫 페이지가 네트워크였다는 이유로 배너를 지우면 안 된다.
                            cacheNotice = CacheNotice.from(cached) ?: it.cacheNotice,
                        )
                    }
                }
                .onFailure { e ->
                    // 추가 로딩 실패는 기존 목록을 지우지 않는다. 이미 본 것까지 사라지면 더 나쁘다.
                    _uiState.update {
                        it.copy(isLoadingMore = false, errorMessage = e.toUserMessage())
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 400L
    }
}

/** 사용자에게 예외 클래스 이름을 보여주지 않는다. */
internal fun Throwable.toUserMessage(): String = when (this) {
    // 저장소가 캐시를 먼저 뒤진 뒤에야 여기로 온다. 그래서 "저장해 둔 것도 없다" 까지 말할 수 있다.
    // 그냥 "네트워크에 연결할 수 없습니다" 로 두면 사용자는 신호가 잡히면 보일 거라 기대하는데,
    // 이 조건은 신호가 돌아와도 다시 검색해야 나온다.
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    -> "네트워크에 연결할 수 없고, 저장해 둔 정보도 없습니다."

    is java.net.SocketTimeoutException -> "응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."
    else -> "정보를 불러오지 못했습니다."
}
