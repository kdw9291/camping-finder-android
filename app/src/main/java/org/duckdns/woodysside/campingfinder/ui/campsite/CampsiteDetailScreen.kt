package org.duckdns.woodysside.campingfinder.ui.campsite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.duckdns.woodysside.campingfinder.R
import org.duckdns.woodysside.campingfinder.data.remote.dto.CampsiteDetailDto
import org.duckdns.woodysside.campingfinder.ui.component.CacheBanner
import org.duckdns.woodysside.campingfinder.ui.component.CampsiteThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampsiteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.campsite?.name ?: stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.errorMessage != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.errorMessage!!)
                        Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                state.campsite != null -> DetailContent(state.campsite!!, state.cacheNotice)
            }
        }
    }
}

@Composable
private fun DetailContent(campsite: CampsiteDetailDto, cacheNotice: CacheNotice?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        // 사진보다 위에 둔다. 이 화면은 현장에서 열리고, 여기 적힌 전화번호와 운영기간을
        // 보고 사람이 움직인다. 그 정보가 언제 것인지가 사진보다 먼저 와야 한다.
        cacheNotice?.let { CacheBanner(it) }

        CampsiteThumbnail(
            imageUrl = campsite.firstImageUrl,
            modifier = Modifier.fillMaxWidth().height(220.dp),
            cornerRadius = 0,
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            campsite.lineIntro?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            campsite.addr1?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            campsite.tel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            InfoRow(stringResource(R.string.pet_policy), campsite.petPolicy.toPetLabel())
            InfoRow(stringResource(R.string.brazier), campsite.brazierType.toBrazierLabel())

            listOfNotNull(
                campsite.toiletCount?.let { stringResource(R.string.toilet_n, it) },
                campsite.showerCount?.let { stringResource(R.string.shower_n, it) },
                campsite.washstandCount?.let { stringResource(R.string.washstand_n, it) },
            ).takeIf { it.isNotEmpty() }?.let {
                InfoRow(stringResource(R.string.counts), it.joinToString(" · "))
            }
        }

        HorizontalDivider()

        // 보유 시설
        campsite.facilities.forEach { (category, values) ->
            if (values.isEmpty()) return@forEach
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = category.toCategoryLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(values.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ★ 정보가 없는 시설군을 감추지 않고 명시한다.
        //
        // "없음" 과 "모름" 을 구분해 보여주는 것이 이 앱의 존재 이유다.
        // 감추면 사용자는 그 시설이 없다고 읽고, 그건 데이터가 뒷받침하지 않는 주장이다.
        if (campsite.unknownCategories.isNotEmpty()) {
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.unknown_section_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                // map 은 inline 이라 람다 안에서도 @Composable 호출이 된다.
                // joinToString 은 inline 이 아니라 그 안에서는 부를 수 없다 — 먼저 변환한다.
                val unknownLabels = campsite.unknownCategories.map { it.toCategoryLabel() }
                Text(
                    text = unknownLabels.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(R.string.unknown_section_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        campsite.intro?.let {
            HorizontalDivider()
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Text(
        text = "$label   $value",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun String?.toPetLabel(): String = when (this) {
    "ALLOWED" -> stringResource(R.string.pet_allowed)
    "SMALL_ONLY" -> stringResource(R.string.pet_small_only)
    "NOT_ALLOWED" -> stringResource(R.string.pet_not_allowed)
    else -> stringResource(R.string.unknown_value)
}

@Composable
private fun String?.toBrazierLabel(): String = when (this) {
    "INDIVIDUAL" -> stringResource(R.string.brazier_individual)
    "COMMUNAL" -> stringResource(R.string.brazier_communal)
    "NOT_ALLOWED" -> stringResource(R.string.brazier_not_allowed)
    else -> stringResource(R.string.unknown_value)
}

@Composable
private fun String.toCategoryLabel(): String = when (this) {
    "SBRS" -> stringResource(R.string.cat_sbrs)
    "POSBL_FCLTY" -> stringResource(R.string.cat_posbl)
    "THEMA_ENVRN" -> stringResource(R.string.cat_thema)
    "LCT" -> stringResource(R.string.cat_lct)
    "INDUTY" -> stringResource(R.string.cat_induty)
    "OPER_PD" -> stringResource(R.string.cat_oper_pd)
    "RESVE" -> stringResource(R.string.cat_resve)
    else -> this
}
