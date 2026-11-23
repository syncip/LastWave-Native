package com.lastwave.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastwave.app.R
import com.lastwave.app.data.lossless.LosslessMusicApi
import com.lastwave.app.playback.MusicPlayer

/** Lets the listener move a YouTube song onto a TIDAL recording the matcher refused (other cut, live, remix). */
@Composable
internal fun TidalVersionsDialog(
    player: MusicPlayer,
    playingDurationMs: Long,
    onSignalPath: () -> Unit,
    onDismiss: () -> Unit,
) {
    val versions by produceState<List<LosslessMusicApi.TidalVersion>?>(null) { value = player.tidalVersions() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_tidal_versions)) },
        text = {
            val list = versions
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Text(stringResource(R.string.player_tidal_versions_none))
                else -> Column {
                    Text(
                        stringResource(R.string.player_tidal_versions_hint, formatMinSec(playingDurationMs / 1000)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                        items(list, key = { it.id }) { version ->
                            ListItem(
                                headlineContent = { Text(version.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            formatMinSec(version.durationSec.toLong()),
                                            "Hi-Res".takeIf { version.hiRes },
                                            version.album.takeIf { it.isNotBlank() },
                                        ).joinToString(" • "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                modifier = Modifier.clickable {
                                    player.playTidalVersion(version.id)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onSignalPath()
            }) { Text(stringResource(R.string.player_signal_path)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun formatMinSec(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
