package com.vabxsen.budgie.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vabxsen.budgie.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun DetailScreen(sub: Subscription, today: LocalDate, onEdit: () -> Unit, onArchive: () -> Unit) {
    val date = sub.nextRenewal(today)
    val days = ChronoUnit.DAYS.between(today, date)
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { Eyebrow("Your collection") }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Brand(sub.brand, 65)
                Column(Modifier.weight(1f)) {
                    Text(sub.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        sub.plan.ifBlank { sub.category.label },
                        color = colors.muted,
                        fontSize = 13.sp,
                    )
                }
                Surface(color = colors.soft, shape = RoundedCornerShape(7.dp)) {
                    Text(
                        sub.status.label,
                        Modifier.padding(8.dp),
                        fontSize = 11.sp,
                        color = colors.muted,
                    )
                }
            }
        }
        item {
            Column {
                Text(money(sub.priceMinor), style = MaterialTheme.typography.displayLarge)
                Text("per ${sub.cycle.suffix}", color = colors.muted, fontSize = 15.sp)
            }
        }
        item {
            if (sub.status == SubscriptionStatus.ARCHIVED) {
                Text("Archived in Budgie. Excluded from spending totals and reminders.",
                    color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            } else Surface(color = Butter, shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.CalendarToday, null, tint = Pine)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (sub.status == SubscriptionStatus.TRIAL) "TRIAL ENDS"
                            else "NEXT RENEWAL",
                            color = Pine.copy(alpha = .7f),
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                            color = Pine,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        if (days == 0L) "Today" else "In $days days",
                        color = Pine,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item {
            Column {
                DetailField("Category", sub.category.label)
                DetailField("Billing cycle", sub.cycle.label)
                DetailField("Monthly equivalent", money(sub.monthlyMinor()))
                DetailField(
                    "Reminder",
                    if (sub.reminderDays == 0) "On renewal day"
                    else "${sub.reminderDays} days before",
                )
                DetailField(
                    "Tracking since",
                    sub.createdDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                )
            }
        }
        if (sub.notes.isNotBlank())
            item {
                Surface(color = colors.soft, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.padding(17.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.EditNote,
                            null,
                            Modifier.size(20.dp),
                            tint = colors.muted,
                        )
                        Text(
                            sub.notes,
                            color = colors.muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        item {
            ActionButton("Edit subscription", onEdit, Modifier.fillMaxWidth(), Icons.Rounded.Edit)
        }
        item {
            ActionButton(
                if (sub.status == SubscriptionStatus.ARCHIVED) "Restore subscription"
                else "Archive subscription",
                onArchive,
                Modifier.fillMaxWidth(),
                if (sub.status == SubscriptionStatus.ARCHIVED) Icons.Rounded.Restore
                else Icons.Rounded.Archive,
                secondary = true,
            )
        }
        item {
            Text(
                "Budgie tracks your subscriptions. Manage billing and cancellations directly with your provider.",
                color = colors.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text(label, color = colors.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value, fontSize = 13.sp)
        }
        HorizontalDivider(color = colors.line)
    }
}

@Composable
fun EditorScreen(
    existing: Subscription?,
    defaultReminder: Int,
    today: LocalDate,
    saving: Boolean = false,
    onSave: (Subscription) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var plan by rememberSaveable { mutableStateOf(existing?.plan ?: "") }
    var amount by rememberSaveable {
        mutableStateOf(
            existing
                ?.priceMinor
                ?.toBigDecimal()
                ?.movePointLeft(2)
                ?.stripTrailingZeros()
                ?.toPlainString() ?: ""
        )
    }
    var cycleName by rememberSaveable {
        mutableStateOf(existing?.cycle?.name ?: BillingCycle.MONTHLY.name)
    }
    var dateText by rememberSaveable {
        mutableStateOf((existing?.anchorDate ?: today.plusDays(1)).toString())
    }
    var categoryName by rememberSaveable {
        mutableStateOf(existing?.category?.name ?: Category.OTHER.name)
    }
    var brand by rememberSaveable { mutableStateOf(existing?.brand ?: "custom") }
    var reminder by rememberSaveable {
        mutableIntStateOf(existing?.reminderDays ?: defaultReminder)
    }
    var trial by rememberSaveable { mutableStateOf(existing?.status == SubscriptionStatus.TRIAL) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var error by rememberSaveable { mutableStateOf("") }
    val date = LocalDate.parse(dateText)
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.imePadding().testTag("subscription-editor"),
    ) {
        item {
            PageTitle(
                "Your collection",
                if (existing == null) "Make room for a\nnew favorite." else "A little fine-tuning.",
                "The next step to a clearer picture.",
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ServiceCatalog.services.forEach { s ->
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .clickable {
                                name = s.name
                                brand = s.brand
                                categoryName = s.category.name
                            }
                            .padding(5.dp)
                            .semantics { contentDescription = "Choose ${s.name}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Brand(s.brand, 42)
                        Spacer(Modifier.height(6.dp))
                        Text(s.name, fontSize = 10.sp)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(80)
                    brand =
                        ServiceCatalog.services.find { s -> s.name.equals(it, true) }?.brand
                            ?: "custom"
                },
                label = { Text("Service name") },
                placeholder = { Text("e.g. Netflix") },
                singleLine = true,
                isError = error.isNotBlank() && name.isBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = plan,
                onValueChange = { plan = it.take(120) },
                label = { Text("Plan name (optional)") },
                placeholder = { Text("e.g. Premium Individual") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (₹)") },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error.isNotBlank() && parseAmount(amount) == null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Enter your actual plan price, including taxes.", fontSize = 11.sp)
                },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Billing cycle", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillingCycle.entries.forEach {
                        Pill(it.label, cycleName == it.name, { cycleName = it.name })
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (existing == null) "Next payment" else "Billing anchor date",
                    fontSize = 13.sp,
                )
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                                context,
                                { _, y, m, d -> dateText = LocalDate.of(y, m + 1, d).toString() },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth,
                            )
                            .apply {
                                datePicker.minDate =
                                    LocalDate.of(1900, 1, 1)
                                        .atStartOfDay(java.time.ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli()
                                datePicker.maxDate =
                                    LocalDate.of(2200, 12, 31)
                                        .atStartOfDay(java.time.ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli()
                            }
                            .show()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.CalendarToday, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
                }
                if (existing != null)
                    Text(
                        "The original billing day is preserved when shorter months roll over.",
                        fontSize = 11.sp,
                        color = colors.muted,
                    )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Category", fontSize = 13.sp)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Category.entries.forEach {
                        Pill(it.label, categoryName == it.name, { categoryName = it.name })
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Remind me", fontSize = 13.sp)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0, 1, 3, 7)
                        .let { if (reminder !in it) it + reminder else it }
                        .sorted()
                        .forEach { d ->
                            Pill(
                                if (d == 0) "On the day" else "$d days before",
                                reminder == d,
                                { reminder = d },
                            )
                        }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(colors.soft, RoundedCornerShape(13.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Currently on a free trial?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Watch it before the first payment.",
                        fontSize = 11.sp,
                        color = colors.muted,
                    )
                }
                Switch(
                    checked = trial,
                    onCheckedChange = { trial = it },
                    modifier = Modifier.semantics { contentDescription = "Free trial" },
                )
            }
        }
        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(2000) },
                label = { Text("A little note (optional)") },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error.isNotBlank())
            item {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        item {
            Text(
                "Trials move into your active spending after their end date. Archive a trial if you cancel it with the provider.",
                color = colors.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            ActionButton(
                if (saving) "Saving…"
                else if (existing == null) "Save subscription" else "Save changes",
                enabled = !saving,
                onClick = {
                    val minor = parseAmount(amount)
                    if (name.isBlank() || minor == null) {
                        error =
                            "Add a service name and a valid amount with up to two decimal places."
                        return@ActionButton
                    }
                    if (trial && date < today) {
                        error = "Choose today or a future date for the trial ending."
                        return@ActionButton
                    }
                    val sub =
                        Subscription(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            plan = plan.trim(),
                            priceMinor = minor,
                            cycle = BillingCycle.valueOf(cycleName),
                            anchorDate = date,
                            category = Category.valueOf(categoryName),
                            brand = brand,
                            status =
                                if (existing?.status == SubscriptionStatus.ARCHIVED)
                                    SubscriptionStatus.ARCHIVED
                                else if (trial) SubscriptionStatus.TRIAL
                                else SubscriptionStatus.ACTIVE,
                            reminderDays = reminder,
                            notes = notes.trim(),
                            createdDate = existing?.createdDate ?: today,
                        )
                    focusManager.clearFocus()
                    keyboard?.hide()
                    onSave(sub)
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Check,
            )
        }
    }
}
