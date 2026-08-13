package org.duckdns.woodysside.campingfinder.ui.campsite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import org.duckdns.woodysside.campingfinder.data.CampsiteRepository
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteDetailDto
import org.duckdns.woodysside.campingfinder.ui.navigation.CampsiteDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampsiteDetailUiState(
    val campsite: CampsiteDetailDto? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    /** 저장해 둔 정보를 보여주고 있으면 채워진다. null 이면 방금 서버에서 받은 것. */
    val cacheNotice: CacheNotice? = null,
)

@HiltViewModel
class CampsiteDetailViewModel @Inject constructor(
    private val repository: CampsiteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // 타입 안전 라우트에서 인자를 꺼낸다. 문자열 키로 꺼내면 오타가 런타임에야 드러난다.
    private val contentId: String = savedStateHandle.toRoute<CampsiteDetail>().contentId

    private val _uiState = MutableStateFlow(CampsiteDetailUiState())
    val uiState: StateFlow<CampsiteDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.detail(contentId) }
                .onSuccess { cached ->
                    _uiState.update {
                        it.copy(
                            campsite = cached.value,
                            isLoading = false,
                            cacheNotice = CacheNotice.from(cached),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) }
                }
        }
    }
}
