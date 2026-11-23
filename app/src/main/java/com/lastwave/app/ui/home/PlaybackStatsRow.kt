package com.lastwave.app.ui.home

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastwave.app.R
import com.lastwave.app.data.repository.PlaybackSourceStats
import com.lastwave.app.data.repository.PlaybackSourceStats.Quality
import java.util.Date

/** Top source, lossless share and data used; a tap opens the full breakdown. */
@Composable
internal fun PlaybackStatsRow(stats: PlaybackSourceStats.Snapshot, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showDetails by remember { mutableStateOf(false) }
    val plays = stats.plays
    val top = stats.playsBySource.maxByOrNull { it.value }
    val lossless = (stats.playsByQuality[Quality.HI_RES] ?: 0) + (stats.playsByQuality[Quality.LOSSLESS] ?: 0)
    val traffic = Formatter.formatShortFileSize(context, stats.wifiBytes + stats.meteredBytes)

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val open = Modifier.weight(1f).clickable { showDetails = true }
        TextPill(top?.let { percent(it.value, plays) } ?: "—", top?.key ?: stringResource(R.string.stats_sources), open)
        TextPill(if (plays > 0) percent(lossless, plays) else "—", stringResource(R.string.stats_quality_lossless), open)
        TextPill(traffic, stringResource(R.string.stats_traffic), open)
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.stats_playback_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.stats_playback_since, DateFormat.getDateFormat(context).format(Date(stats.sinceMillis)), plays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Section(stringResource(R.string.stats_sources))
                    stats.playsBySource.entries.sortedByDescending { it.value }.forEach {
                        DetailLine(it.key, "${percent(it.value, plays)} · ${it.value}")
                    }
                    Section(stringResource(R.string.stats_quality))
                    listOf(
                        Quality.HI_RES to R.string.stats_quality_hires,
                        Quality.LOSSLESS to R.string.stats_quality_lossless,
                        Quality.LOSSY to R.string.stats_quality_lossy,
                    ).forEach { (quality, label) ->
                        val count = stats.playsByQuality[quality] ?: 0
                        DetailLine(stringResource(label), "${percent(count, plays)} · $count")
                    }
                    Section(stringResource(R.string.stats_traffic))
                    DetailLine(stringResource(R.string.stats_traffic_wifi), Formatter.formatShortFileSize(context, stats.wifiBytes))
                    DetailLine(stringResource(R.string.stats_traffic_metered), Formatter.formatShortFileSize(context, stats.meteredBytes))
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text(stringResource(R.string.common_done)) }
            },
        )
    }
}

@Composable
private fun TextPill(value: String, label: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = StatPillShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun percent(part: Int, total: Int): String = if (total <= 0) "—" else "${part * 100 / total} %"
