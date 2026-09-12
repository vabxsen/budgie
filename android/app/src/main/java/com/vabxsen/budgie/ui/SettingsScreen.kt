package com.vabxsen.budgie.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vabxsen.budgie.R
import com.vabxsen.budgie.BuildConfig
import com.vabxsen.budgie.auth.AccountState
import com.vabxsen.budgie.domain.*

@Composable
fun SettingsScreen(
    prefs: Preferences,
    permissionGranted: Boolean,
    onPreferences: ((Preferences) -> Preferences) -> Unit,
    onBudget: () -> Unit,
    onNotifications: (Boolean) -> Unit,
    onSystemNotifications: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onImport: () -> Unit,
    account: AccountState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    var appearancePicker by remember { mutableStateOf(false) }
    var reminderPicker by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            PageTitle(
                "Make yourself at home",
                "Your space.\nYour way.",
                "The small details that make Budgie yours.",
            )
        }
        item { AccountSection(account, onSignIn, onSignOut) }
        item { Text("The everyday essentials", style = MaterialTheme.typography.titleLarge) }
        item {
            Column {
                SettingRow(
                    Icons.Rounded.AccountBalanceWallet,
                    "Monthly budget",
                    "A little boundary for recurring spending.",
                    if (prefs.budgetMinor == 0L) "Not set" else money(prefs.budgetMinor),
                    onBudget,
                )
                SettingRow(
                    Icons.Rounded.Payments,
                    "Currency",
                    "Amounts are tracked in Indian rupees.",
                    "INR (₹)",
                )
                SettingRow(
                    Icons.Rounded.LightMode,
                    "Appearance",
                    "A look that feels right.",
                    prefs.appearance.label,
                    { appearancePicker = true },
                )
            }
        }
        item { Text("A friendly nudge", style = MaterialTheme.typography.titleLarge) }
        item {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.NotificationsNone, null, tint = colors.muted)
                    Column(Modifier.weight(1f)) {
                        Text("Renewal reminders", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (prefs.notifications && !permissionGranted)
                                "Blocked in Android settings."
                            else "A heads-up before the next payment.",
                            color = colors.muted,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = prefs.notifications && permissionGranted,
                        onCheckedChange = onNotifications,
                        modifier = Modifier.semantics { contentDescription = "Renewal reminders" },
                    )
                }
                HorizontalDivider(color = colors.line)
                SettingRow(
                    Icons.Rounded.Event,
                    "Default reminder",
                    "Used for new subscriptions.",
                    if (prefs.reminderDays == 0) "On the day"
                    else "${prefs.reminderDays} days before",
                    { reminderPicker = true },
                )
                SettingRow(
                    Icons.Rounded.Tune,
                    "Android notification settings",
                    "Sound, lock screen, and delivery.",
                    "Open",
                    onSystemNotifications,
                )
                Text(
                    "Android schedules reminders in the background. Battery settings may affect when they arrive.",
                    color = colors.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { Text("Always in your hands", style = MaterialTheme.typography.titleLarge) }
        item {
            Column {
                SettingRow(
                    Icons.Rounded.FileDownload,
                    "Export subscriptions",
                    "Take your collection as a CSV file.",
                    "Export",
                    onExport,
                )
                SettingRow(
                    Icons.Rounded.Backup,
                    "Back up your collection",
                    "Save subscriptions and preferences to a file you choose.",
                    "Back up",
                    onBackup,
                )
                SettingRow(
                    Icons.Rounded.Restore,
                    "Restore a backup",
                    "Import a Budgie Android backup file.",
                    "Restore",
                    onImport,
                )
            }
        }
        item {
            Surface(color = Butter, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(27.dp), verticalArrangement = Arrangement.spacedBy(19.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_bird),
                        null,
                        tint = Pine,
                        modifier = Modifier.size(52.dp),
                    )
                    Text(
                        "A little bird.\nA clearer picture.",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Pine,
                    )
                    Text(
                        "Budgie gives your subscriptions a home, so you can spend more thoughtfully and live a little lighter.",
                        color = Pine.copy(alpha = .75f),
                        fontSize = 14.sp,
                    )
                    HorizontalDivider(color = Pine.copy(alpha = .14f))
                    Text("Budgie · Version ${BuildConfig.VERSION_NAME}", color = Pine.copy(alpha = .7f), fontSize = 12.sp)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = colors.muted)
                Text("Private by design.", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Your collection stays in this app’s private storage. Optional Google sign-in sends account information to Google and Firebase for authentication. Subscription data is not uploaded. Exported backups contain your subscription details; choose a place you trust.",
                    color = colors.muted,
                    fontSize = 13.sp,
                )
            }
        }
    }
    if (appearancePicker)
        AlertDialog(
            onDismissRequest = { appearancePicker = false },
            title = { Text("A look that feels right.") },
            text = {
                Column {
                    Appearance.entries.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onPreferences { it.copy(appearance = a) }
                                    appearancePicker = false
                                }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = prefs.appearance == a,
                                onClick = {
                                    onPreferences { it.copy(appearance = a) }
                                    appearancePicker = false
                                },
                            )
                            Text(a.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { appearancePicker = false }) { Text("Done") } },
        )
    if (reminderPicker)
        AlertDialog(
            onDismissRequest = { reminderPicker = false },
            title = { Text("A friendly heads-up.") },
            text = {
                Column {
                    listOf(0, 1, 3, 7).forEach { d ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onPreferences { it.copy(reminderDays = d) }
                                    reminderPicker = false
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = prefs.reminderDays == d,
                                onClick = {
                                    onPreferences { it.copy(reminderDays = d) }
                                    reminderPicker = false
                                },
                            )
                            Text(if (d == 0) "On renewal day" else "$d days before")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reminderPicker = false }) { Text("Done") } },
        )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    description: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = colors.muted)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    color = colors.muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    value,
                    fontSize = 12.sp,
                    color = colors.ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (onClick != null)
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = colors.muted,
                    modifier = Modifier.size(18.dp),
                )
        }
        HorizontalDivider(color = colors.line)
    }
}
