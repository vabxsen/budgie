package com.vabxsen.budgie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vabxsen.budgie.R
import com.vabxsen.budgie.auth.AccountState
import com.vabxsen.budgie.data.SyncState
import com.vabxsen.budgie.data.SyncStatus

@Composable
fun AccountSection(
    state: AccountState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    syncState: SyncState = SyncState(),
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your Budgie account", style = MaterialTheme.typography.titleLarge)
        Surface(color = Butter, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.AccountCircle, null, Modifier.size(44.dp), tint = Pine)
                    Column(Modifier.weight(1f)) {
                        Text(if (state.profile != null) "SIGNED IN" else "YOUR PERSONAL SPACE", color = Pine.copy(alpha = .7f), fontSize = 10.sp)
                        Text(state.profile?.name ?: "Make yourself at home.", color = Pine, style = MaterialTheme.typography.titleLarge)
                        state.profile?.email?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Pine.copy(alpha = .8f), fontSize = 13.sp)
                        }
                    }
                }
                Text(
                    if (state.profile == null)
                        "Sign in with Google to keep this collection available across your Android devices."
                    else "Your Google account is connected to Budgie.",
                    color = Pine.copy(alpha = .85f), fontSize = 13.sp,
                )
                if (state.profile != null) {
                    val (icon, label) =
                        when (syncState.status) {
                            SyncStatus.CONNECTING -> Icons.Rounded.CloudSync to "Connecting to your cloud collection…"
                            SyncStatus.SYNCING -> Icons.Rounded.CloudSync to "Saving changes to your account…"
                            SyncStatus.SYNCED -> Icons.Rounded.CloudDone to "Your collection is synced"
                            SyncStatus.OFFLINE -> Icons.Rounded.CloudOff to
                                "You’re offline. Changes will sync when you reconnect."
                            SyncStatus.ERROR -> Icons.Rounded.CloudOff to
                                (syncState.message ?: "Sync paused. Your changes are safe on this device.")
                            SyncStatus.SIGNED_OUT -> Icons.Rounded.CloudSync to "Preparing account sync…"
                        }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(icon, null, Modifier.size(18.dp), tint = Pine.copy(alpha = .72f))
                        Text(label, color = Pine.copy(alpha = .8f), fontSize = 12.sp)
                    }
                }
                if (state.profile == null) {
                    OutlinedButton(
                        onClick = onSignIn, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Pine),
                        border = BorderStroke(1.dp, Pine.copy(alpha = .2f)),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Pine, strokeWidth = 2.dp)
                        else Icon(painterResource(R.drawable.brand_google), null, Modifier.size(18.dp), tint = Color.Unspecified)
                        Spacer(Modifier.width(10.dp))
                        Text(if (state.busy) "Signing in…" else "Sign in with Google")
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmSignOut = true }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Pine),
                    ) { Text(if (state.busy) "Signing out…" else "Sign out") }
                }
                Text(
                    if (state.profile == null)
                        "You can keep using a separate, private workspace without signing in."
                    else "Offline edits stay on this device and upload when you reconnect.",
                    color = Pine.copy(alpha = .75f),
                    fontSize = 12.sp,
                )
            }
        }
        state.message?.let {
            Text(it, color = colors.muted, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out of Budgie?") },
        text = { Text("Sync will pause and Budgie will return to the signed-out workspace. Your account collection remains saved on this device and in your account.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay signed in") } },
    )
}
