package top.yogiczy.mytv.tv.ui.screensold.epg.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.isLive
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EpgProgrammeItem(
    modifier: Modifier = Modifier,
    epgProgrammeProvider: () -> EpgProgramme = { EpgProgramme() },
    supportPlaybackProvider: () -> Boolean = { false },
    isPlaybackProvider: () -> Boolean = { false },
    hasReservedProvider: () -> Boolean = { false },
    onPlayback: () -> Unit = {},
    onReserve: () -> Unit = {},
    focusOnLive: Boolean = true,
) {
    val programme = epgProgrammeProvider()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val isPlayback = isPlaybackProvider()
    val isLive = programme.isLive()

    var isFocused by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val localContentColor = LocalContentColor.current
    val containerColor = remember(isFocused, isLive, isPlayback) {
        if (isFocused) colorScheme.onSurface
        else if (isLive || isPlayback) colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else Color.Transparent
    }
    val contentColor = remember(isFocused, isLive, isPlayback) {
        if (isFocused) colorScheme.surface
        else if (isLive || isPlayback) colorScheme.onSurface
        else localContentColor
    }

    Box(
        modifier = modifier
            .ifElse(
                (isPlayback || (isLive && focusOnLive)) && focusOnLive,
                Modifier.focusOnLaunchedSaveable()
            )
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusable()
            .fillMaxWidth()
            .sizeIn(minHeight = 40.dp)
            .background(containerColor, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .handleKeyEvents(
                onSelect = {
                    if (programme.endAt < System.currentTimeMillis() && supportPlaybackProvider()) onPlayback()
                    else if (programme.startAt > System.currentTimeMillis()) onReserve()
                }
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${timeFormat.format(programme.startAt)}    ${programme.title}",
                maxLines = if (isFocused) Int.MAX_VALUE else 1,
                color = contentColor,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )

            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalTextStyle provides MaterialTheme.typography.labelSmall,
            ) {
                if (programme.isLive()) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (isPlaybackProvider()) {
                    Text("正在回放")
                } else if (programme.endAt < System.currentTimeMillis() && supportPlaybackProvider()) {
                    Text("回放")
                } else if (programme.startAt > System.currentTimeMillis()) {
                    if (hasReservedProvider()) Text("已预约")
                    else Text("预约")
                }
            }
        }
    }
}

@Preview
@Composable
private fun EpgProgrammeItemPreview() {
    MyTvTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            EpgProgrammeItem(
                epgProgrammeProvider = { EpgProgramme.EXAMPLE },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() - 200000,
                        endAt = System.currentTimeMillis() - 100000,
                    )
                },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() + 100000,
                        endAt = System.currentTimeMillis() + 200000,
                    )
                },
            )
            EpgProgrammeItem(
                epgProgrammeProvider = {
                    EpgProgramme.EXAMPLE.copy(
                        startAt = System.currentTimeMillis() + 100000,
                        endAt = System.currentTimeMillis() + 200000,
                    )
                },
                hasReservedProvider = { true },
            )
        }
    }
}