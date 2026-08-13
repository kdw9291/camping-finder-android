package org.duckdns.woodysside.campingfinder.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.duckdns.woodysside.campingfinder.R
import org.duckdns.woodysside.campingfinder.ui.campsite.CacheNotice

/**
 * "저장해 둔 정보를 보고 있다" 는 사실을 알리는 띠.
 *
 * <p>경고색(error)을 쓰지 않는다. 이것은 잘못된 상태가 아니라 <b>의도한 동작</b>이다 —
 * 신호가 없는 곳에서 앱이 제 역할을 하고 있다는 뜻이다.
 * 빨간 띠를 띄우면 사용자는 앱이 고장 났다고 읽고, 정작 정보는 멀쩡히 있는데 닫아 버린다.
 *
 * <p>대신 나이를 반드시 말한다. "오프라인" 만 알리고 언제 받은 것인지 감추면,
 * 사용자는 눈앞의 운영시간이 어제 것인지 지난달 것인지 판단할 수 없다.
 */
@Composable
fun CacheBanner(notice: CacheNotice, modifier: Modifier = Modifier) {
    Text(
        text = when (notice.ageDays) {
            0 -> stringResource(R.string.cache_notice_today)
            1 -> stringResource(R.string.cache_notice_yesterday)
            else -> stringResource(R.string.cache_notice_days_ago, notice.ageDays)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
