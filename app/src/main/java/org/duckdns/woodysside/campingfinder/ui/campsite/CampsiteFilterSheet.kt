package org.duckdns.woodysside.campingfinder.ui.campsite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import org.duckdns.woodysside.campingfinder.R
import org.duckdns.woodysside.campingfinder.data.remote.dto.FilterOptionDto

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CampsiteFilterSheet(
    current: CampsiteFilter,
    options: FilterOptions,
    onApply: (CampsiteFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    // 시트 안에서만 편집하고, "적용"을 눌러야 검색이 나간다.
    // 칩을 누를 때마다 요청하면 조합을 고르는 동안 목록이 계속 흔들린다.
    var draft by remember { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { draft = draft.clear() }) {
                    Text(stringResource(R.string.filter_reset))
                }
            }

            // ★ 이 프로젝트의 핵심이 사용자 손에 들어오는 지점.
            //
            // 부대시설의 28.7%가 빈 값이다. 이 스위치를 끄면 "전기 있는 곳" 검색에서
            // 849곳이 사라진다. 그 849곳은 전기가 없는 게 아니라 정보가 없는 곳이다.
            // 기본값을 켬으로 두고, 끄는 것이 무슨 뜻인지 설명을 붙인다.
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.filter_include_unknown),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.filter_include_unknown_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = draft.includeUnknown,
                    onCheckedChange = { draft = draft.copy(includeUnknown = it) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Text(stringResource(R.string.filter_pet), style = MaterialTheme.typography.titleSmall)
            FilterChip(
                selected = draft.petAllowed,
                onClick = { draft = draft.copy(petAllowed = !draft.petAllowed) },
                label = { Text(stringResource(R.string.filter_pet_allowed)) },
            )

            if (options.induty.isNotEmpty()) {
                Text(
                    stringResource(R.string.filter_induty),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ChipGroup(
                    options = options.induty,
                    selected = draft.induty,
                    onToggle = { draft = draft.toggleInduty(it) },
                )
            }

            if (options.sbrs.isNotEmpty()) {
                Text(
                    stringResource(R.string.filter_sbrs),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.filter_sbrs_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipGroup(
                    options = options.sbrs,
                    selected = draft.sbrs,
                    onToggle = { draft = draft.toggleSbrs(it) },
                )
            }

            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.filter_apply))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    options: List<FilterOptionDto>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option.code in selected,
                onClick = { onToggle(option.code) },
                label = { Text(option.label) },
            )
        }
    }
}
