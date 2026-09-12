package com.vabxsen.budgie

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vabxsen.budgie.data.CollectionCodec
import com.vabxsen.budgie.domain.*
import com.vabxsen.budgie.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BudgieViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as BudgieApplication).repository
    val state = repository.state
    private val mutableSaving = MutableStateFlow(false)
    val saving = mutableSaving.asStateFlow()
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun message(text: String) {
        viewModelScope.launch { messageChannel.send(text) }
    }

    fun retry() {
        viewModelScope.launch { repository.load() }
    }

    private fun change(success: String, transform: (BudgieCollection) -> BudgieCollection) {
        viewModelScope.launch {
            runCatching { repository.update(transform) }
                .onSuccess {
                    if (success.isNotBlank()) message(success)
                    ReminderScheduler.checkNow(getApplication())
                }
                .onFailure { message("Couldn’t save your changes. Please try again.") }
        }
    }

    fun onboard() =
        change("") {
            it.copy(
                preferences = it.preferences.copy(onboarded = true),
            )
        }

    fun save(subscription: Subscription, onSaved: () -> Unit) {
        if (mutableSaving.value) return
        mutableSaving.value = true
        viewModelScope.launch {
            try {
                runCatching {
                    repository.update { c ->
                        c.copy(
                            subscriptions =
                                if (c.subscriptions.any { it.id == subscription.id })
                                    c.subscriptions.map {
                                        if (it.id == subscription.id) subscription else it
                                    }
                                else c.subscriptions + subscription
                        )
                    }
                }
                    .onSuccess {
                        onSaved()
                        message("${subscription.name} saved. All in a good place.")
                        ReminderScheduler.checkNow(getApplication())
                    }
                    .onFailure { message("Couldn’t save your changes. Please try again.") }
            } finally {
                mutableSaving.value = false
            }
        }
    }

    fun archive(subscription: Subscription) =
        change(
            if (subscription.status == SubscriptionStatus.ARCHIVED) "Subscription restored."
            else "Archived in Budgie. Your provider subscription is unchanged."
        ) { c ->
            c.copy(
                subscriptions =
                    c.subscriptions.map {
                        if (it.id == subscription.id)
                            it.copy(
                                status =
                                    if (it.status == SubscriptionStatus.ARCHIVED)
                                        SubscriptionStatus.ACTIVE
                                    else SubscriptionStatus.ARCHIVED
                            )
                        else it
                    }
            )
        }

    fun preferences(transform: (Preferences) -> Preferences) =
        change("") { it.copy(preferences = transform(it.preferences)) }

    fun export(uri: Uri, csv: Boolean) {
        viewModelScope.launch {
            val collection = state.value?.getOrNull() ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>()
                        .contentResolver
                        .openOutputStream(uri, "wt")
                        ?.bufferedWriter()
                        ?.use {
                            it.write(
                                if (csv) CollectionCodec.csv(collection)
                                else CollectionCodec.encode(collection)
                            )
                        } ?: error("Cannot open this file.")
                }
            }
                .onSuccess {
                    message(if (csv) "Subscription export saved." else "Your backup is saved.")
                }
                .onFailure { message("Couldn’t save that file. Please choose another location.") }
        }
    }

    fun import(uri: Uri, onReady: (BudgieCollection) -> Unit) {
        viewModelScope.launch {
            runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes =
                            getApplication<Application>()
                                .contentResolver
                                .openInputStream(uri)
                                ?.use { it.readBytesLimited(CollectionCodec.MAX_BACKUP_BYTES) }
                                ?: error("Cannot open file")
                        CollectionCodec.decode(bytes.toString(Charsets.UTF_8))
                    }
                }
                .onSuccess(onReady)
                .onFailure {
                    message(
                        "This isn’t a valid Budgie Android backup. Your collection is unchanged."
                    )
                }
        }
    }

    fun restore(collection: BudgieCollection) =
        change("Your collection is restored.") { old ->
            collection.copy(
                preferences =
                    collection.preferences.copy(
                        onboarded = true,
                        notifications = old.preferences.notifications,
                    )
            )
        }

    private fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            require(out.size() + n <= max)
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
