package org.duckdns.woodysside.campingfinder.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.duckdns.woodysside.campingfinder.data.CampsiteRepository
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteSummaryDto
import org.duckdns.woodysside.campingfinder.ui.campsite.toUserMessage

data class CampsiteMapUiState(
    val markers: List<CampsiteSummaryDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searched: Boolean = false,

    /** 서버가 돌려준 전체 건수. 마커 상한(100)에 걸렸는지 판단하는 데 쓴다. */
    val totalInArea: Long = 0,
) {
    /** 반경 안에 상한보다 많은 캠핑장이 있으면 일부만 보이는 것이다. 그 사실을 숨기지 않는다. */
    val truncated: Boolean
        get() = totalInArea > markers.size
}

@HiltViewModel
class CampsiteMapViewModel @Inject constructor(
    private val repository: CampsiteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampsiteMapUiState())
    val uiState: StateFlow<CampsiteMapUiState> = _uiState.asStateFlow()

    fun searchArea(lat: Double, lng: Double, zoom: Double) {
        val radiusKm = radiusForZoom(zoom)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching { repository.nearby(lat, lng, radiusKm) }
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            markers = response.content,
                            totalInArea = response.totalElements,
                            isLoading = false,
                            searched = true,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        /**
         * 줌 레벨에서 검색 반경을 정한다.
         *
         * <p>화면에 보이는 범위와 검색 범위가 크게 어긋나면 사용자가 "여기 있는데 왜 안 나오지" 를 겪는다.
         * 정밀하게 계산할 수도 있지만, 마커 상한이 100건이라 그 이상 정확해도 의미가 없다.
         */
        fun radiusForZoom(zoom: Double): Double = when {
            zoom >= 13 -> 3.0
            zoom >= 11 -> 10.0
            zoom >= 9 -> 30.0
            zoom >= 7 -> 80.0
            else -> 200.0
        }
    }
}
