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
import java.time.LocalDate
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

    fun checkNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "budgie-check-reminders",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>().build(),
            )
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
            // Refresh visible reminders after edits without re-alerting or re-posting dismissed ones.
            if (ledger.getString(sub.id, null) == token && posted.none { it.tag == tag }) continue
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
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .build(),
                )
                ledger.edit { putString(sub.id, token) }
            } catch (_: SecurityException) {
                return Result.success()
            }
        }
        // Bound the delivery ledger to current subscriptions.
        val ids = collection.subscriptions.map { it.id }.toSet()
        ledger.edit { ledger.all.keys.filter { it !in ids }.forEach { remove(it) } }
        return Result.success()
    }
}
