package com.lastwave.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lastwave.app.R
import com.lastwave.app.data.lossless.LosslessMusicApi
import com.lastwave.app.data.tidal.TidalAccount
import com.lastwave.app.data.tidal.TidalSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TidalAccountViewModel @Inject constructor(
    private val tidalAccount: TidalAccount,
    private val losslessMusicApi: LosslessMusicApi,
) : ViewModel() {
    val session: StateFlow<TidalSession?> = tidalAccount.session
    val clientConfigured: Boolean get() = tidalAccount.hasClientCredentials

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun loginUrl(): String = tidalAccount.loginUrl()

    fun isRedirect(url: String): Boolean = tidalAccount.isRedirect(url)

    fun completeLogin(redirectUrl: String) {
        viewModelScope.launch {
            tidalAccount.completeLogin(redirectUrl).fold(
                onSuccess = {
                    losslessMusicApi.invalidateCredentialsCache()
                    _notice.value = "TIDAL connected" + (it.username.takeIf(String::isNotBlank)?.let { name -> ": $name" } ?: "")
                },
                onFailure = { _notice.value = "TIDAL login failed: ${it.message}" },
            )
        }
    }

    fun logout() {
        tidalAccount.logout()
        losslessMusicApi.invalidateCredentialsCache()
        _notice.value = "TIDAL disconnected"
    }

    fun clearNotice() {
        _notice.value = null
    }
}

/** Settings -> Sources: sign in to / out of the user's own TIDAL account. */
@Composable
internal fun TidalAccountSection(viewModel: TidalAccountViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var loginUrl by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }
    }

    loginUrl?.let { url ->
        TidalLoginDialog(
            loginUrl = url,
            isRedirect = viewModel::isRedirect,
            onRedirect = { redirect ->
                loginUrl = null
                viewModel.completeLogin(redirect)
            },
            onDismiss = { loginUrl = null },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.settings_tidal_disconnect)) },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; viewModel.logout() }) {
                    Text(stringResource(R.string.settings_tidal_disconnect))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("TIDAL")
        SettingsGroup(rowCount = 1) { _, position ->
            val current = session
            SettingsActionCard(
                icon = Icons.Filled.MusicNote,
                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                title = if (current == null) stringResource(R.string.settings_tidal_connect) else stringResource(R.string.settings_tidal_disconnect),
                subtitle = when {
                    current != null -> stringResource(
                        R.string.settings_tidal_connected,
                        current.username.ifBlank { "TIDAL" },
                        current.countryCode,
                    )
                    !viewModel.clientConfigured -> "Missing TIDAL_CLIENT_ID in local.properties; rebuild the app."
                    else -> stringResource(R.string.settings_tidal_connect_sub)
                },
                danger = current != null,
                onClick = {
                    when {
                        current != null -> confirmLogout = true
                        viewModel.clientConfigured -> loginUrl = viewModel.loginUrl()
                    }
                },
                position = position,
                isHighlighted = false,
            )
        }
    }
}
