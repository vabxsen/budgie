package com.vabxsen.budgie

import android.app.Application
import com.vabxsen.budgie.data.BudgieRepository
import com.vabxsen.budgie.notifications.ReminderScheduler
import com.vabxsen.budgie.updates.cleanupInstalledUpdateFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class BudgieApplication : Application() {
    val repository by lazy { BudgieRepository(this) }

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            cleanupInstalledUpdateFiles(this@BudgieApplication)
            repository.load()
            // Edits, cloud updates and account switches all refresh reminders; bursts are coalesced.
            repository.state.filterNotNull().collectLatest { result ->
                if (result.isSuccess) {
                    delay(500)
                    ReminderScheduler.schedule(this@BudgieApplication)
                    ReminderScheduler.checkNow(this@BudgieApplication)
                }
            }
        }
    }
}
