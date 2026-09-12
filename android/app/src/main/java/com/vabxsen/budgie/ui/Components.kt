package com.vabxsen.budgie.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vabxsen.budgie.R
import com.vabxsen.budgie.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

fun categoryColor(category: Category) =
    when (category) {
        Category.ENTERTAINMENT -> Color(0xFFDF8667)
        Category.PRODUCTIVITY -> Color(0xFF929ADB)
        Category.MUSIC -> Color(0xFFA8C994)
        Category.STORAGE -> Color(0xFFE3BE66)
        Category.OTHER -> Color(0xFFA7AFB5)
    }

@Composable
fun Brand(brand: String, size: Int = 44) {
    val res =
        when (brand) {
            "netflix" -> R.drawable.brand_netflix
            "spotify" -> R.drawable.brand_spotify
            "youtube" -> R.drawable.brand_youtube
            "notion" -> R.drawable.brand_notion
            "figma" -> R.drawable.brand_figma
            "google" -> R.drawable.brand_google
            "icloud" -> R.drawable.brand_icloud
            else -> null
        }
    val tint =
        when (brand) {
            "netflix",
            "youtube" -> Color(0xFFFFF0EC)
            "spotify" -> Color(0xFFEAF3E7)
            "figma" -> Color(0xFFEEEBFB)
            "google",
            "icloud",
            "prime" -> Color(0xFFEDF2FB)
            else -> Color(0xFFF0EDE6)
        }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * .27).dp)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        if (res != null)
            Image(
                painterResource(res),
                contentDescription = null,
                modifier = Modifier.size((size * .53).dp),
            )
        else
            Icon(
                if (brand == "prime") Icons.Rounded.PlayCircle else Icons.Rounded.Layers,
                contentDescription = null,
                tint = Pine,
                modifier = Modifier.size((size * .54).dp),
            )
    }
}

@Composable
fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted,
        letterSpacing = 1.2.sp,
    )
}

@Composable
fun PageTitle(eyebrow: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.headlineLarge, color = colors.ink)
        Text(description, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    secondary: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(11.dp),
        border = if (secondary) BorderStroke(1.dp, colors.line) else null,
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (secondary) colors.surface else if (colors.dark) Butter else Pine,
                contentColor =
                    if (secondary) colors.ink else if (colors.dark) Pine else Color.White,
            ),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null)
            TextButton(onClick = onAction) {
                Text(action, color = colors.ink, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(5.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    null,
                    Modifier.size(15.dp),
                    tint = colors.ink,
                )
            }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(68.dp).background(colors.soft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Layers, null, tint = colors.muted, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            color = colors.muted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) ActionButton(action, onAction, icon = Icons.Rounded.Add)
    }
}

@Composable
fun SubscriptionCard(
    sub: Subscription,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Column {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Brand(sub.brand, 38)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(7.dp).background(categoryColor(sub.category), CircleShape))
                }
                Spacer(Modifier.height(17.dp))
                Text(
                    sub.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    sub.plan.ifBlank { sub.category.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    money(sub.priceMinor),
                    fontFamily = DisplayFont,
                    fontSize = 29.sp,
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                )
                Text(
                    "per ${sub.cycle.suffix}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            HorizontalDivider(color = colors.line)
            Row(
                Modifier.fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(4.dp)
                        .background(
                            if (sub.status == SubscriptionStatus.ARCHIVED) colors.muted
                            else Color(0xFF8EA67A),
                            CircleShape,
                        )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (sub.status == SubscriptionStatus.ARCHIVED) "Archived"
                    else
                        "${if(sub.status==SubscriptionStatus.TRIAL) "Trial ·" else "Renews"} ${sub.nextRenewal(today).format(ShortDate)}",
                    fontSize = 11.sp,
                    color = colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SubscriptionRow(
    sub: Subscription,
    today: LocalDate,
    onClick: () -> Unit,
    date: LocalDate = sub.nextRenewal(today),
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Brand(sub.brand, 42)
        Column(Modifier.weight(1f)) {
            Text(
                sub.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${date.format(ShortDate)} · ${sub.cycle.label}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        Text(money(sub.priceMinor), fontFamily = DisplayFont, fontSize = 19.sp)
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(17.dp), tint = colors.muted)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) Butter else colors.soft,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            color = if (selected) Pine else colors.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
