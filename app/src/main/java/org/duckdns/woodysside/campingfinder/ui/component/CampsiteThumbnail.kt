package org.duckdns.woodysside.campingfinder.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.duckdns.woodysside.campingfinder.R

/**
 * 캠핑장 썸네일.
 *
 * <p>이미지가 없을 때 회색 네모만 남기지 않는다. 그러면 <b>로딩 중인지 이미지가 없는 건지
 * 구분되지 않아</b> 사용자가 기다리게 된다. "사진 없음" 을 명시하는 편이 정직하다.
 *
 * <p>시설 정보를 "모름" 으로 표시하는 것과 같은 원칙이다 — 없는 것을 없다고 말한다.
 */
@Composable
fun CampsiteThumbnail(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.no_photo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp),
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
