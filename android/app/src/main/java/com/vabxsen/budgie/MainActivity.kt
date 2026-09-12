package com.vabxsen.budgie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vabxsen.budgie.auth.AccountViewModel
import kotlinx.coroutines.launch
import com.vabxsen.budgie.domain.*
import com.vabxsen.budgie.ui.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    private var notificationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationId = intent.getStringExtra("subscription_id")
        setContent {
            BudgieRoot(
                notificationId,
                consumed = {
                    notificationId = null
                    intent.removeExtra("subscription_id")
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationId = intent.getStringExtra("subscription_id")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BudgieRoot(
        openId: String?,
        consumed: () -> Unit,
        vm: BudgieViewModel = viewModel(),
    ) {
        val accountVm: AccountViewModel = viewModel()
        val account by accountVm.state.collectAsStateWithLifecycle()
        val accountScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val result by vm.state.collectAsStateWithLifecycle()
        var today by remember { mutableStateOf(LocalDate.now()) }
        val collection = result?.getOrNull()?.onDate(today)
        val saving by vm.saving.collectAsStateWithLifecycle()
        var permission by remember {
            mutableStateOf(NotificationManagerCompat.from(this).areNotificationsEnabled())
        }
        val lifecycle = LocalLifecycleOwner.current
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    today = LocalDate.now()
                    permission =
                        NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                }
            }
            lifecycle.lifecycle.addObserver(observer)
            onDispose { lifecycle.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(60_000)
                today = LocalDate.now()
            }
        }
        var screen by rememberSaveable { mutableStateOf("home") }
        var parent by rememberSaveable { mutableStateOf("home") }
        var remindersParent by rememberSaveable { mutableStateOf("home") }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var editingId by rememberSaveable { mutableStateOf<String?>(null) }
        var confirmArchive by remember { mutableStateOf(false) }
        var confirmDiscard by remember { mutableStateOf(false) }
        var budgetDialog by remember { mutableStateOf(false) }
        var imported by remember { mutableStateOf<BudgieCollection?>(null) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
        val requestPermission =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted
                ->
                permission = granted
                vm.preferences { it.copy(notifications = granted) }
                if (!granted)
                    vm.message("Notifications are off. You can enable them in Android settings.")
            }
        val backup =
            rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) vm.export(uri, false)
            }
        val export =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
                uri ->
                if (uri != null) vm.export(uri, true)
            }
        val restore =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) vm.import(uri) { imported = it }
            }
        val notifications: (Boolean) -> Unit = { enabled ->
            if (!enabled) vm.preferences { it.copy(notifications = false) }
            else if (
                Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
            )
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                vm.message("Notifications are blocked in Android settings.")
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            } else {
                permission = true
                vm.preferences { it.copy(notifications = true) }
            }
        }
        val add: () -> Unit = {
            parent = screen
            editingId = null
            screen = "edit"
        }
        val open: (Subscription) -> Unit = { s ->
            parent = screen
            selectedId = s.id
            screen = "detail"
        }
        val back: () -> Unit = {
            if (screen == "edit") {
                if (!saving) {
                    focusManager.clearFocus()
                    confirmDiscard = true
                }
            } else if (screen == "reminders") screen = remindersParent
            else screen = parent.takeIf { it !in listOf("edit", "detail") } ?: "home"
        }
        BackHandler(screen !in listOf("home")) {
            if (screen in listOf("edit", "detail")) back() else screen = "home"
        }
        LaunchedEffect(openId, collection) {
            if (openId != null && collection != null) {
                if (collection.subscriptions.any { it.id == openId }) {
                    selectedId = openId
                    parent = "home"
                    screen = "detail"
                }
                consumed()
            }
        }
        BudgieTheme(collection?.preferences?.appearance ?: Appearance.SYSTEM) {
            val dark = colors.dark
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            Scaffold(
                containerColor = colors.background,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    if (collection?.preferences?.onboarded == true)
                        Column(Modifier.statusBarsPadding()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 15.dp).height(58.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (screen in listOf("detail", "edit", "reminders")) {
                                    IconButton(onClick = back) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                                    }
                                    Text(
                                        when (screen) {
                                            "detail" -> "Subscription details"
                                            "edit" ->
                                                if (editingId == null) "Add subscription"
                                                else "Edit subscription"
                                            else -> "Reminders"
                                        },
                                        fontSize = 16.sp,
                                    )
                                } else {
                                    Icon(
                                        painterResource(R.drawable.ic_bird),
                                        null,
                                        Modifier.size(26.dp),
                                        tint = colors.ink,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("budgie", fontFamily = DisplayFont, fontSize = 29.sp)
                                }
                                Spacer(Modifier.weight(1f))
                                if (screen !in listOf("edit", "detail", "reminders")) {
                                    Text(
                                        today.format(DateTimeFormatter.ofPattern("d MMM")),
                                        fontSize = 11.sp,
                                        color = colors.muted,
                                    )
                                    IconButton(
                                        onClick = {
                                            remindersParent = screen
                                            screen = "reminders"
                                        }
                                    ) {
                                        Icon(Icons.Rounded.NotificationsNone, "Open reminders")
                                    }
                                }
                            }
                            HorizontalDivider(color = colors.line)
                        }
                },
                bottomBar = {
                    if (
                        collection?.preferences?.onboarded == true &&
                            screen !in listOf("detail", "edit", "reminders")
                    ) {
                        NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                            listOf(
                                    Triple("home", "Home", Icons.Rounded.Dashboard),
                                    Triple("collection", "Subscriptions", Icons.Rounded.Layers),
                                    Triple("calendar", "Calendar", Icons.Rounded.CalendarMonth),
                                    Triple("insights", "Insights", Icons.Rounded.BarChart),
                                    Triple("settings", "Settings", Icons.Rounded.Settings),
                                )
                                .forEach { (id, label, icon) ->
                                    NavigationBarItem(
                                        selected = screen == id,
                                        onClick = { screen = id },
                                        icon = { Icon(icon, null, Modifier.size(22.dp)) },
                                        label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                                        colors =
                                            NavigationBarItemDefaults.colors(
                                                indicatorColor = Butter,
                                                selectedIconColor = Pine,
                                                selectedTextColor = colors.ink,
                                                unselectedIconColor = colors.muted,
                                                unselectedTextColor = colors.muted,
                                            ),
                                    )
                                }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when {
                        result == null ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        collection == null ->
                            EmptyState(
                                "Your collection needs a moment.",
                                "Budgie couldn’t read its saved data. Your existing file is untouched.",
                                "Try again",
                                vm::retry,
                            )
                        !collection.preferences.onboarded ->
                            WelcomeScreen(vm::onboard)
                        screen == "home" ->
                            HomeScreen(
                                collection,
                                today,
                                add,
                                open,
                                { screen = "collection" },
                                { screen = "calendar" },
                                { screen = "insights" },
                                { budgetDialog = true },
                            )
                        screen == "collection" -> CollectionScreen(collection, today, add, open)
                        screen == "calendar" -> CalendarScreen(collection, today, open)
                        screen == "insights" ->
                            InsightsScreen(
                                collection,
                                today,
                                open,
                                { export.launch("Budgie-subscriptions-$today.csv") },
                            )
                        screen == "reminders" ->
                            RemindersScreen(
                                collection.copy(
                                    preferences =
                                        collection.preferences.copy(
                                            notifications =
                                                collection.preferences.notifications && permission
                                        )
                                ),
                                today,
                                open,
                                notifications,
                                { screen = "settings" },
                            )
                        screen == "settings" ->
                            SettingsScreen(
                                collection.preferences,
                                permission,
                                vm::preferences,
                                { budgetDialog = true },
                                notifications,
                                {
                                    startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    )
                                },
                                { export.launch("Budgie-subscriptions-$today.csv") },
                                { backup.launch("Budgie-backup-$today.json") },
                                { restore.launch(arrayOf("application/json", "text/plain")) },
                                account,
                                { accountScope.launch { accountVm.signIn(this@MainActivity) } },
                                { accountScope.launch { accountVm.signOut(this@MainActivity) } },
                            )
                        screen == "detail" -> {
                            val sub = collection.subscriptions.find { it.id == selectedId }
                            if (sub != null)
                                DetailScreen(
                                    sub,
                                    today,
                                    {
                                        editingId = sub.id
                                        screen = "edit"
                                    },
                                    {
                                        if (sub.status == SubscriptionStatus.ARCHIVED)
                                            vm.archive(sub)
                                        else confirmArchive = true
                                    },
                                )
                            else
                                EmptyState(
                                    "This subscription isn’t here.",
                                    "It may have changed when you restored a backup.",
                                    "Back to collection",
                                    { screen = "collection" },
                                )
                        }
                        screen == "edit" ->
                            EditorScreen(
                                collection.subscriptions.find { it.id == editingId },
                                collection.preferences.reminderDays,
                                today,
                                saving,
                            ) { sub ->
                                vm.save(sub) {
                                    selectedId = sub.id
                                    screen = "detail"
                                }
                            }
                    }
                }
            }
            if (confirmArchive)
                AlertDialog(
                    onDismissRequest = { confirmArchive = false },
                    title = { Text("Archive this subscription?") },
                    text = {
                        Text(
                            "It will leave your spending totals and move to Archived. You can restore it any time. This does not cancel payments with your provider."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                collection
                                    ?.subscriptions
                                    ?.find { it.id == selectedId }
                                    ?.let(vm::archive)
                                confirmArchive = false
                            }
                        ) {
                            Text("Archive")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmArchive = false }) {
                            Text("Keep subscription")
                        }
                    },
                )
            if (confirmDiscard)
                AlertDialog(
                    onDismissRequest = { confirmDiscard = false },
                    title = { Text("Leave this form?") },
                    text = { Text("Unsaved changes will be discarded.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmDiscard = false
                                screen = if (editingId != null) "detail" else parent
                            }
                        ) {
                            Text("Discard changes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
                    },
                )
            if (imported != null)
                AlertDialog(
                    onDismissRequest = { imported = null },
                    title = { Text("Restore your collection?") },
                    text = {
                        Text(
                            "Replace your current subscriptions with ${imported!!.subscriptions.size} subscriptions from this backup? Export your current collection first if you want to keep a copy."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                vm.restore(imported!!)
                                imported = null
                                screen = "home"
                            }
                        ) {
                            Text("Restore collection")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { imported = null }) { Text("Keep current") }
                    },
                )
            if (budgetDialog && collection != null)
                BudgetDialog(collection.preferences.budgetMinor, { budgetDialog = false }) { minor
                    ->
                    vm.preferences { it.copy(budgetMinor = minor) }
                    budgetDialog = false
                }
        }
    }
}

@Composable
private fun BudgetDialog(current: Long, onClose: () -> Unit, onSave: (Long) -> Unit) {
    var amount by rememberSaveable {
        mutableStateOf(if (current == 0L) "" else current.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString())
    }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("A little breathing room.") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose a monthly limit that feels right. We’ll help you see where you stand.")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monthly budget (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error,
                )
                if (error)
                    Text(
                        "Enter a valid budget of at least ₹1.",
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = parseAmount(amount)
                    if (value == null || value < 100) error = true else onSave(value)
                }
            ) {
                Text("Save budget")
            }
        },
        dismissButton = {
            Row {
                if (current > 0) TextButton(onClick = { onSave(0) }) { Text("Remove budget") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
    )
}
