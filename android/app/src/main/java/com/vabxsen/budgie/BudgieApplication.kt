package com.vabxsen.budgie

import android.app.Application
import com.vabxsen.budgie.data.BudgieRepository
import com.vabxsen.budgie.notifications.ReminderScheduler
import com.vabxsen.budgie.updates.cleanupInstalledUpdateFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
            repository.state.filterNotNull().collect { result ->
                if (result.isSuccess) ReminderScheduler.schedule(this@BudgieApplication)
            }
        }
    }
}
