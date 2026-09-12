package com.vabxsen.budgie.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vabxsen.budgie.R
import com.vabxsen.budgie.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(
    collection: BudgieCollection,
    today: LocalDate,
    onAdd: () -> Unit,
    onOpen: (Subscription) -> Unit,
    onLibrary: () -> Unit,
    onCalendar: () -> Unit,
    onInsights: () -> Unit,
    onBudget: () -> Unit,
) {
    val active = collection.subscriptions.filter { it.status == SubscriptionStatus.ACTIVE }
    val upcoming =
        collection.subscriptions
            .filter { it.status != SubscriptionStatus.ARCHIVED }
            .sortedBy { it.nextRenewal(today) }
    val total = collection.subscriptions.monthlyTotal()
    var yearly by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(23.dp),
    ) {
        item {
            PageTitle(
                "A little clarity, every day",
                "Good things.\nOn repeat.",
                "Your subscriptions, finally in a good place.",
            )
        }
        item { ActionButton("Add subscription", onAdd, icon = Icons.Rounded.Add) }
        item {
            Surface(color = Butter, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.AccountBalanceWallet,
                            null,
                            Modifier.size(18.dp),
                            tint = Pine,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "RECURRING SPEND",
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = Pine,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(color = Pine.copy(alpha = .06f), shape = RoundedCornerShape(8.dp)) {
                            Row {
                                listOf(false, true).forEach { value ->
                                    TextButton(
                                        onClick = { yearly = value },
                                        contentPadding = PaddingValues(horizontal = 9.dp),
                                        modifier = Modifier.height(36.dp),
                                        colors =
                                            ButtonDefaults.textButtonColors(contentColor = Pine),
                                    ) {
                                        Text(
                                            if (value) "Year" else "Month",
                                            fontSize = 11.sp,
                                            fontWeight =
                                                if (value == yearly) FontWeight.Bold
                                                else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                money(if (yearly) total * 12.toBigDecimal() else total),
                                style = MaterialTheme.typography.displayLarge,
                                color = Pine,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (yearly) "per year · current estimate" else "per month",
                                color = Pine.copy(alpha = .7f),
                                fontSize = 12.sp,
                            )
                        }
                        Box(
                            Modifier.size(63.dp)
                                .rotate(12f)
                                .border(1.dp, Pine.copy(alpha = .25f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.NorthEast,
                                null,
                                tint = Pine,
                                modifier = Modifier.size(29.dp),
                            )
                        }
                    }
                    Text(
                        "${active.size} subscriptions. One clear picture.",
                        fontSize = 12.sp,
                        color = Pine.copy(alpha = .72f),
                    )
                    HorizontalDivider(color = Pine.copy(alpha = .12f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
                            active.take(4).forEach { Brand(it.brand, 30) }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = onInsights,
                            colors = ButtonDefaults.textButtonColors(contentColor = Pine),
                        ) {
                            Text("See the breakdown", fontSize = 11.sp)
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Rounded.NorthEast, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat(
                    "Annual outlook",
                    money(total * 12.toBigDecimal()),
                    "At your current pace",
                    Modifier.weight(1f),
                )
                MiniStat(
                    "Next 7 days",
                    money(
                        upcoming
                            .filter { ChronoUnit.DAYS.between(today, it.nextRenewal(today)) <= 7 }
                            .sumOf { it.priceMinor }
                    ),
                    "Upcoming payments",
                    Modifier.weight(1f),
                )
                MiniStat(
                    "Active plans",
                    active.size.toString().padStart(2, '0'),
                    "Everything you love",
                    Modifier.weight(1f),
                )
            }
        }
        item { SectionHeading("Your little collection", "View all", onLibrary) }
        if (active.isEmpty())
            item {
                EmptyState(
                    "Start your collection.",
                    "Add your first subscription and make space for a clearer picture.",
                    "Add subscription",
                    onAdd,
                )
            }
        items(active.take(6).chunked(2), key = { it.first().id }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { sub ->
                    SubscriptionCard(sub, today, { onOpen(sub) }, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (upcoming.isNotEmpty())
            item {
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, colors.line),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        SectionHeading("Up next", "Calendar", onCalendar)
                        upcoming.take(3).forEach { SubscriptionRow(it, today, { onOpen(it) }) }
                    }
                }
            }
        item { BudgetCard(total, collection.preferences.budgetMinor, onBudget) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.VerifiedUser,
                    null,
                    tint = colors.muted,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "A space that’s just yours. Stored on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, caption: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 11.sp, color = colors.muted)
        Text(
            value,
            fontFamily = DisplayFont,
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(caption, fontSize = 10.sp, color = colors.muted)
    }
}

@Composable
fun BudgetCard(total: BigDecimal, budget: Long, onEdit: () -> Unit) {
    val remainder = budget.toBigDecimal() - total
    Surface(color = Pine, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DonutLarge, null, tint = Butter)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, "Edit monthly budget", tint = Color(0xFFBFC8B4))
                }
            }
            Text(
                "A little breathing room.",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                if (remainder.signum() >= 0) "You’re keeping things in balance."
                else "A good moment to review what you use.",
                color = Color(0xFFBFC8B4),
                fontSize = 12.sp,
            )
            Text(
                money(remainder.abs()),
                color = Color.White,
                fontFamily = DisplayFont,
                fontSize = 38.sp,
            )
            Text(
                if (remainder.signum() >= 0) "left in your monthly budget"
                else "over your monthly budget",
                color = Color(0xFFBFC8B4),
                fontSize = 12.sp,
            )
            LinearProgressIndicator(
                progress = { (total.toFloat() / budget).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Butter,
                trackColor = Color(0xFF515B49),
                drawStopIndicator = {},
            )
            Row {
                Text("${money(total)} committed", fontSize = 11.sp, color = Color(0xFFBFC8B4))
                Spacer(Modifier.weight(1f))
                Text("${money(budget)} budget", fontSize = 11.sp, color = Color(0xFFBFC8B4))
            }
        }
    }
}

@Composable
fun CollectionScreen(
    collection: BudgieCollection,
    today: LocalDate,
    onAdd: () -> Unit,
    onOpen: (Subscription) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("All") }
    var category by rememberSaveable { mutableStateOf("All") }
    var grid by rememberSaveable { mutableStateOf(true) }
    val results =
        collection.subscriptions.filter { s ->
            (if (status == "All") s.status != SubscriptionStatus.ARCHIVED
            else s.status.name == status) &&
                (category == "All" || s.category.name == category) &&
                ("${s.name} ${s.plan}".contains(query, true))
        }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageTitle(
                "A home for your favorites",
                "Your subscriptions.",
                "The things you love. And what they cost.",
            )
        }
        item {
            ActionButton(
                "Add subscription",
                onAdd,
                icon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(Butter, RoundedCornerShape(15.dp))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Monthly commitment", color = Pine.copy(alpha = .7f), fontSize = 12.sp)
                    Text(
                        money(collection.subscriptions.monthlyTotal()),
                        fontFamily = DisplayFont,
                        fontSize = 32.sp,
                        color = Pine,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Active plans", fontSize = 12.sp, color = Pine.copy(alpha = .7f))
                    Text(
                        collection.subscriptions
                            .count { it.status == SubscriptionStatus.ACTIVE }
                            .toString()
                            .padStart(2, '0'),
                        fontFamily = DisplayFont,
                        fontSize = 32.sp,
                        color = Pine,
                    )
                }
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                        "All" to "All",
                        "ACTIVE" to "Active",
                        "TRIAL" to "Free trials",
                        "ARCHIVED" to "Archived",
                    )
                    .forEach { (value, label) -> Pill(label, status == value, { status = value }) }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Find a subscription…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty())
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, "Clear search")
                        }
                },
                label = { Text("Search subscriptions") },
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Pill("All categories", category == "All", { category = "All" })
                    Category.entries.forEach {
                        Pill(it.label, category == it.name, { category = it.name })
                    }
                }
                IconButton(onClick = { grid = !grid }) {
                    Icon(
                        if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        if (grid) "Switch to list" else "Switch to grid",
                    )
                }
            }
        }
        if (results.isEmpty())
            item {
                EmptyState(
                    if (query.isNotBlank()) "No matches this time." else "Nothing here just yet.",
                    if (query.isNotBlank()) "Try another name or category."
                    else "Your subscriptions will have a place here.",
                )
            }
        if (grid)
            items(results.chunked(2), key = { it.first().id }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { s ->
                        SubscriptionCard(s, today, { onOpen(s) }, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        else items(results, key = { it.id }) { SubscriptionRow(it, today, { onOpen(it) }) }
    }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit, onSamples: () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_bird), null, Modifier.size(32.dp), tint = colors.ink)
            Spacer(Modifier.width(8.dp))
            Text(
                "budgie",
                fontFamily = DisplayFont,
                fontSize = 31.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().height(210.dp).background(Butter, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_bird),
                null,
                Modifier.size(115.dp).rotate(-12f),
                tint = Pine,
            )
        }
        Eyebrow("A little less to think about")
        Text(
            "Good things.\nOn repeat.",
            style = MaterialTheme.typography.headlineLarge,
            fontSize = 43.sp,
            lineHeight = 47.sp,
        )
        Text(
            "Give your subscriptions a home. See what you spend, know what’s next, and make room for what matters.",
            color = colors.muted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.VerifiedUser,
                null,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Private on your device. No account needed.",
                fontSize = 12.sp,
                color = colors.muted,
            )
        }
        ActionButton(
            "Start my collection",
            onStart,
            Modifier.fillMaxWidth(),
            Icons.AutoMirrored.Rounded.ArrowForward,
        )
        TextButton(onClick = onSamples, modifier = Modifier.fillMaxWidth()) {
            Text("Explore with sample data", color = colors.muted)
        }
        Text(
            "Amounts are tracked in Indian rupees (₹).",
            fontSize = 11.sp,
            color = colors.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
