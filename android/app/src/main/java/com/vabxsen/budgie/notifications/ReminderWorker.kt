package com.vabxsen.budgie.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.*
import com.vabxsen.budgie.BudgieApplication
import com.vabxsen.budgie.MainActivity
import com.vabxsen.budgie.R
import com.vabxsen.budgie.domain.SubscriptionStatus
import com.vabxsen.budgie.domain.money
import com.vabxsen.budgie.domain.onDate
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

object ReminderScheduler {
    const val CHANNEL = "budgie_renewals"

    fun createChannel(context: Context) {
        context
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                        CHANNEL,
                        "Renewal reminders",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                    .apply {
                        description =
                            "A friendly heads-up before subscription renewals and trial endings."
                    }
            )
    }

    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            "budgie-periodic-reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    /** Checks again when quiet hours end, so a reminder that arrived silently can sound once. */
    fun checkAfterQuietHours(context: Context) {
        val now = LocalDateTime.now()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "budgie-after-quiet-hours",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(Duration.between(now, quietHoursEnd(now)))
                    .build(),
            )
    }

    fun checkNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "budgie-check-reminders",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>().build(),
            )
    }
}

private val QUIET_START: LocalTime = LocalTime.of(22, 0)
private val QUIET_END: LocalTime = LocalTime.of(7, 0)

private const val QUIET_SUFFIX = "|quiet"

/** Reminders posted overnight still appear, but without sound or vibration. */
internal fun isQuietHours(time: LocalTime): Boolean = time >= QUIET_START || time < QUIET_END

/** The next time quiet hours end, strictly after [now]. */
internal fun quietHoursEnd(now: LocalDateTime): LocalDateTime =
    now.toLocalDate().atTime(QUIET_END).let { if (it.isAfter(now)) it else it.plusDays(1) }

internal data class ReminderDelivery(
    val post: Boolean,
    val silent: Boolean = false,
    val alertAgain: Boolean = false,
    val ledgerToken: String,
)

/**
 * How to deliver one reminder. [delivered] is the ledger entry from earlier runs, [token] names this
 * renewal, and [showing] says whether its notification is still in the shade.
 */
internal fun reminderDelivery(delivered: String?, token: String, showing: Boolean, quiet: Boolean): ReminderDelivery {
    val quietToken = token + QUIET_SUFFIX
    val alreadyDelivered = delivered == token || delivered == quietToken
    return when {
        // Dismissed after it was shown: never post it again.
        alreadyDelivered && !showing -> ReminderDelivery(post = false, ledgerToken = token)
        // Arrived silently overnight and is still unread: sound once now that quiet hours are over.
        delivered == quietToken && !quiet -> ReminderDelivery(post = true, alertAgain = true, ledgerToken = token)
        // Still showing: refresh its text after edits without another alert.
        alreadyDelivered -> ReminderDelivery(post = true, silent = quiet, ledgerToken = delivered ?: token)
        quiet -> ReminderDelivery(post = true, silent = true, ledgerToken = quietToken)
        else -> ReminderDelivery(post = true, ledgerToken = token)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val collection =
            (applicationContext as BudgieApplication)
                .repository
                .state
                .filterNotNull()
                .first()
                .getOrNull()
                ?.onDate(LocalDate.now()) ?: return Result.retry()
        val notifications = NotificationManagerCompat.from(applicationContext)
        if (!collection.preferences.notifications) {
            notifications.cancelAll()
            return Result.success()
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return Result.success()
        if (!notifications.areNotificationsEnabled()) return Result.success()
        val ledger =
            applicationContext.getSharedPreferences("reminder-delivery", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val quiet = isQuietHours(LocalTime.now())
        var postedQuietly = false
        val eligible = collection.subscriptions.filter {
            it.status != SubscriptionStatus.ARCHIVED &&
                ChronoUnit.DAYS.between(today, it.nextRenewal(today)) <= it.reminderDays
        }
        val eligibleTags = eligible.map { "budgie:${it.id}" }.toSet()
        val posted = applicationContext.getSystemService(NotificationManager::class.java).activeNotifications
        posted.filter { it.tag !in eligibleTags }.forEach { notifications.cancel(it.tag, it.id) }
        for (sub in eligible) {
            val renewal = sub.nextRenewal(today)
            val days = ChronoUnit.DAYS.between(today, renewal)
            val token = "${renewal}|${sub.reminderDays}"
            val tag = "budgie:${sub.id}"
            val delivered = ledger.getString(sub.id, null)
            val delivery = reminderDelivery(delivered, token, posted.any { it.tag == tag }, quiet)
            if (!delivery.post) {
                if (delivered != delivery.ledgerToken) ledger.edit { putString(sub.id, delivery.ledgerToken) }
                continue
            }
            val intent =
                Intent(applicationContext, MainActivity::class.java)
                    .setData(Uri.Builder().scheme("budgie").authority("subscription").appendPath(sub.id).build())
                    .putExtra("subscription_id", sub.id)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending =
                PendingIntent.getActivity(
                    applicationContext,
                    sub.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val whenText =
                when (days) {
                    0L -> "today"
                    1L -> "tomorrow"
                    else -> "in $days days"
                }
            val title =
                if (sub.status == SubscriptionStatus.TRIAL) "${sub.name} trial ends $whenText"
                else "${sub.name} renews $whenText"
            val text =
                "${money(sub.priceMinor)} · ${sub.cycle.label}. A little heads-up from Budgie."
            try {
                notifications.notify(
                    tag,
                    0,
                    NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL)
                        .setSmallIcon(R.drawable.ic_bird)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setContentIntent(pending)
                        .setOnlyAlertOnce(!delivery.alertAgain)
                        .setSilent(delivery.silent)
                        .setAutoCancel(true)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .build(),
                )
                ledger.edit { putString(sub.id, delivery.ledgerToken) }
                if (delivery.ledgerToken.endsWith(QUIET_SUFFIX)) postedQuietly = true
            } catch (_: SecurityException) {
                return Result.success()
            }
        }
        if (postedQuietly) ReminderScheduler.checkAfterQuietHours(applicationContext)
        // Bound the delivery ledger to current subscriptions.
        val ids = collection.subscriptions.map { it.id }.toSet()
        ledger.edit { ledger.all.keys.filter { it !in ids }.forEach { remove(it) } }
        return Result.success()
    }
}
