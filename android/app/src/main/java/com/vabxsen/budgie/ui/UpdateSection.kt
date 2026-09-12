package com.vabxsen.budgie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vabxsen.budgie.BuildConfig
import com.vabxsen.budgie.updates.UpdateUiState
import kotlin.math.roundToInt

@Composable
fun UpdateSection(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onAllowInstalls: () -> Unit,
) {
    val copy = updateCopy(state)
    Surface(
        color = colors.soft,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).semantics { contentDescription = "App updates" },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = Butter, shape = RoundedCornerShape(14.dp)) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        null,
                        tint = Pine,
                        modifier = Modifier.size(48.dp).padding(12.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("App updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Installed · v${BuildConfig.VERSION_NAME}",
                        color = colors.muted,
                        fontSize = 11.sp,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(copy.title, style = MaterialTheme.typography.titleLarge)
                Text(copy.description, color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (state is UpdateUiState.Downloading) {
                val percent = (state.progress * 100).roundToInt()
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
                        contentDescription = "Update download $percent percent"
                    },
                )
                Text("$percent% downloaded", color = colors.muted, fontSize = 11.sp)
            }
            Button(
                onClick = when (state) {
                    is UpdateUiState.Available -> onDownload
                    is UpdateUiState.PermissionRequired -> onAllowInstalls
                    else -> onCheck
                },
                enabled = state !is UpdateUiState.Checking &&
                    state !is UpdateUiState.Downloading &&
                    state !is UpdateUiState.Ready &&
                    state !is UpdateUiState.Installing,
                colors = ButtonDefaults.buttonColors(containerColor = Pine, contentColor = Butter),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (copy.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Butter,
                    )
                    Spacer(Modifier.width(9.dp))
                } else if (state is UpdateUiState.Available) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                }
                Text(copy.button)
            }
        }
    }
}

private data class UpdateCopy(
    val title: String,
    val description: String,
    val button: String,
    val busy: Boolean = false,
)

private fun updateCopy(state: UpdateUiState): UpdateCopy = when (state) {
    UpdateUiState.Idle -> UpdateCopy(
        "Check for updates",
        "Get the newest signed Budgie release directly from GitHub.",
        "Check for updates",
    )
    UpdateUiState.Checking -> UpdateCopy(
        "Looking for something new",
        "Checking the latest published GitHub Release…",
        "Checking…",
        true,
    )
    is UpdateUiState.UpToDate -> UpdateCopy(
        "You’re up to date",
        "Budgie v${state.version} is the newest published version.",
        "Check again",
    )
    is UpdateUiState.Available -> UpdateCopy(
        "Budgie v${state.release.version} is ready",
        "${formatBytes(state.release.sizeBytes)} · Downloaded only from the official Budgie GitHub release.",
        "Download & install",
    )
    is UpdateUiState.Downloading -> UpdateCopy(
        "Downloading v${state.release.version}",
        "Budgie will verify the file and its signing certificate before installation.",
        "Downloading…",
        true,
    )
    is UpdateUiState.PermissionRequired -> UpdateCopy(
        "Allow Budgie to install this update",
        "Android needs one-time permission for updates downloaded outside Google Play.",
        "Open Android settings",
    )
    is UpdateUiState.Ready -> UpdateCopy(
        "Preparing Android’s installer",
        "Your subscriptions stay safely on this device during the update.",
        "Preparing…",
        true,
    )
    is UpdateUiState.Installing -> UpdateCopy(
        "Ready for your confirmation",
        "Confirm the replacement in Android’s installer. The previous app version is replaced after installation.",
        "Installer opened",
        true,
    )
    is UpdateUiState.Error -> UpdateCopy(
        "Update paused",
        state.message,
        "Try again",
    )
}

private fun formatBytes(bytes: Long): String =
    if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0)
    else "%.0f KB".format(bytes / 1_024.0)
