package com.vabxsen.budgie.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vabxsen.budgie.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun CalendarScreen(collection: BudgieCollection, today: LocalDate, onOpen: (Subscription) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val renewals =
        collection.subscriptions
            .flatMap { s -> s.renewalsIn(month).map { s to it } }
            .sortedBy { it.second }
    val shown =
        if (selected == 0) renewals else renewals.filter { it.second.dayOfMonth == selected }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            PageTitle(
                "No more surprises",
                "A date with your dues.",
                "A clear view of what’s coming. Room to plan ahead.",
            )
        }
        item {
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.line),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                monthText = month.minusMonths(1).toString()
                                selected = 0
                            }
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, "Previous month")
                        }
                        IconButton(
                            onClick = {
                                monthText = month.plusMonths(1).toString()
                                selected = 0
                            }
                        ) {
                            Icon(Icons.Rounded.ChevronRight, "Next month")
                        }
                    }
                    Row {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                            Text(
                                it,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = colors.muted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    val cells =
                        List(month.atDay(1).dayOfWeek.value - 1) { 0 } +
                            (1..month.lengthOfMonth()).toList()
                    cells.chunked(7).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (i in 0..6) {
                                val day = week.getOrNull(i) ?: 0
                                if (day == 0) Spacer(Modifier.weight(1f).height(74.dp))
                                else {
                                    val date = month.atDay(day)
                                    val due = renewals.filter { it.second == date }
                                    val isToday = date == today
                                    val bg =
                                        when {
                                            isToday -> Butter
                                            due.isNotEmpty() -> colors.soft
                                            else -> colors.background
                                        }
                                    Column(
                                        Modifier.weight(1f)
                                            .height(74.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(bg)
                                            .then(
                                                if (selected == day)
                                                    Modifier.border(
                                                        1.dp,
                                                        Color(0xFF82946A),
                                                        RoundedCornerShape(9.dp),
                                                    )
                                                else Modifier
                                            )
                                            .clickable {
                                                selected = if (selected == day) 0 else day
                                            }
                                            .semantics {
                                                contentDescription = "$date, ${due.size} payments"
                                            }
                                            .padding(vertical = 7.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(
                                            day.toString(),
                                            fontSize = 12.sp,
                                            color = if (isToday) Pine else colors.ink,
                                        )
                                        if (due.isNotEmpty()) {
                                            Brand(due.first().first.brand, 22)
                                            if (due.size > 1)
                                                Text(
                                                    "+${due.size-1}",
                                                    fontSize = 8.sp,
                                                    color = colors.muted,
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(Butter, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("Today", fontSize = 10.sp, color = colors.muted)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                monthText = YearMonth.from(today).toString()
                                selected = today.dayOfMonth
                            }
                        ) {
                            Text("Back to today", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Planned payments")
                    Text(
                        money(renewals.sumOf { it.first.priceMinor }),
                        fontFamily = DisplayFont,
                        fontSize = 35.sp,
                    )
                    Text(
                        "${renewals.size} renewals this month",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
                if (selected != 0) TextButton(onClick = { selected = 0 }) { Text("Show all") }
            }
        }
        item {
            Text(
                if (selected == 0) "This month"
                else month.atDay(selected).format(DateTimeFormatter.ofPattern("d MMMM")),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (shown.isEmpty())
            item {
                EmptyState(
                    "A little breathing room.",
                    "No payments ${if(selected==0) "this month" else "on this date"}.",
                )
            }
        items(shown, key = { "${it.first.id}-${it.second}" }) { (s, date) ->
            SubscriptionRow(s, today, { onOpen(s) }, date)
        }
    }
}

@Composable
fun InsightsScreen(
    collection: BudgieCollection,
    today: LocalDate,
    onOpen: (Subscription) -> Unit,
    onExport: () -> Unit,
) {
    val active = collection.subscriptions.filter { it.status == SubscriptionStatus.ACTIVE }
    val total = active.monthlyTotal()
    var yearly by rememberSaveable { mutableStateOf(false) }
    val grouped =
        Category.entries
            .map { c -> c to active.filter { it.category == c }.monthlyTotal() }
            .filter { it.second.signum() > 0 }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            PageTitle(
                "The bigger picture",
                "Know where it goes.",
                "Small payments add up. Let’s make them make sense.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Monthly", !yearly, { yearly = false })
                Pill("Yearly", yearly, { yearly = true })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onExport) {
                    Icon(Icons.Rounded.FileDownload, "Export subscriptions")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your ${if(yearly) "annual" else "monthly"} commitment", color = colors.muted)
                Text(
                    money(if (yearly) total * 12.toBigDecimal() else total),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    "${active.size} active subscriptions · Current estimate",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
        if (active.isEmpty())
            item {
                EmptyState(
                    "Your bigger picture starts here.",
                    "Add a subscription to see your spending breakdown.",
                )
            }
        else {
            item {
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, colors.line),
                ) {
                    Column(
                        Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        Text("A look ahead", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Projected spend at your current rates",
                            fontSize = 12.sp,
                            color = colors.muted,
                        )
                        Row(
                            Modifier.fillMaxWidth().height(190.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            repeat(if (yearly) 3 else 6) { index ->
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .height(140.dp)
                                            .clip(
                                                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                            )
                                            .background(if (index == 0) Butter else colors.soft)
                                            .semantics {
                                                contentDescription =
                                                    "Projected spending ${money(if(yearly) total*12.toBigDecimal() else total)}"
                                            }
                                    )
                                    Text(
                                        if (yearly) (today.year + index).toString()
                                        else
                                            YearMonth.from(today)
                                                .plusMonths(index.toLong())
                                                .format(DateTimeFormatter.ofPattern("MMM")),
                                        fontSize = 10.sp,
                                        color = colors.muted,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = colors.line)
                        Text(
                            "Estimates assume no plan or price changes.",
                            fontSize = 11.sp,
                            color = colors.muted,
                        )
                    }
                }
            }
            item {
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, colors.line),
                ) {
                    Column(
                        Modifier.padding(23.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("A little of everything.", style = MaterialTheme.typography.titleLarge)
                        Text("Spending by category", color = colors.muted, fontSize = 12.sp)
                        Box(
                            Modifier.size(188.dp).align(Alignment.CenterHorizontally),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                                var start = -90f
                                grouped.forEach { (category, amount) ->
                                    val sweep = amount.toFloat() / total.toFloat() * 360f
                                    drawArc(
                                        categoryColor(category),
                                        start,
                                        sweep - 1.5f,
                                        false,
                                        style = Stroke(25.dp.toPx()),
                                    )
                                    start += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    active.size.toString(),
                                    fontFamily = DisplayFont,
                                    fontSize = 38.sp,
                                )
                                Text("subscriptions", fontSize = 11.sp, color = colors.muted)
                            }
                        }
                        grouped.forEach { (c, amount) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                Box(Modifier.size(8.dp).background(categoryColor(c), CircleShape))
                                Text(c.label, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(
                                    money(if (yearly) amount * 12.toBigDecimal() else amount),
                                    fontSize = 13.sp,
                                )
                                Text(
                                    "${(amount.toFloat()/total.toFloat()*100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = colors.muted,
                                )
                            }
                        }
                    }
                }
            }
            item { SectionHeading("The biggest pieces") }
            items(active.sortedByDescending { it.monthlyMinor() }, key = { it.id }) { s ->
                SubscriptionRow(s, today, { onOpen(s) })
            }
        }
    }
}

@Composable
fun RemindersScreen(
    collection: BudgieCollection,
    today: LocalDate,
    onOpen: (Subscription) -> Unit,
    onEnable: (Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    var trials by rememberSaveable { mutableStateOf(false) }
    val upcoming =
        collection.subscriptions
            .filter {
                it.status != SubscriptionStatus.ARCHIVED &&
                    (!trials || it.status == SubscriptionStatus.TRIAL)
            }
            .sortedBy { it.nextRenewal(today) }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(21.dp),
    ) {
        item {
            PageTitle(
                "A friendly heads-up",
                "Stay one step ahead.",
                "A small nudge before the next payment.",
            )
        }
        item {
            Surface(color = Butter, shape = RoundedCornerShape(18.dp)) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = Pine)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "A little reminder.\nA lot less worry.",
                            color = Pine,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (collection.preferences.notifications)
                                "Renewal reminders are enabled."
                            else "Turn on reminders for a heads-up.",
                            color = Pine.copy(alpha = .7f),
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = collection.preferences.notifications,
                        onCheckedChange = onEnable,
                        modifier =
                            Modifier.semantics { contentDescription = "Enable renewal reminders" },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Upcoming", !trials, { trials = false })
                Pill("Free trials", trials, { trials = true })
            }
        }
        if (upcoming.isEmpty())
            item {
                EmptyState(
                    "Nothing to keep an eye on.",
                    "Your ${if(trials) "trial" else "renewal"} reminders will appear here.",
                )
            }
        items(upcoming, key = { it.id }) { s ->
            val date = s.nextRenewal(today)
            val days = ChronoUnit.DAYS.between(today, date)
            Surface(
                onClick = { onOpen(s) },
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.line),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Brand(s.brand)
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (days == 0L) "Renews today"
                                else "In $days days · ${date.format(ShortDate)}",
                                fontSize = 12.sp,
                                color = colors.muted,
                            )
                        }
                        Text(money(s.priceMinor), fontFamily = DisplayFont, fontSize = 21.sp)
                    }
                    HorizontalDivider(color = colors.line)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            Icons.Rounded.NotificationsNone,
                            null,
                            Modifier.size(15.dp),
                            tint = colors.muted,
                        )
                        Text(
                            if (s.reminderDays == 0) "Reminder on the renewal day"
                            else "Reminder ${s.reminderDays} days before renewal",
                            fontSize = 11.sp,
                            color = colors.muted,
                        )
                    }
                }
            }
        }
        item {
            ActionButton(
                "Reminder settings",
                onSettings,
                Modifier.fillMaxWidth(),
                Icons.Rounded.Settings,
                secondary = true,
            )
        }
    }
}
