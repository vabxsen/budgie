package com.vabxsen.budgie.data

/** An in-memory stand-in for Firestore, shared by several simulated phones. */
internal class FakeCloud {
    private val subscriptions = mutableMapOf<String, MutableMap<String, Map<String, Any>>>()
    private val preferences = mutableMapOf<String, Map<String, Any>>()
    private val devices = mutableListOf<FakeDevice>()

    fun device(): FakeDevice = FakeDevice(this).also { devices += it }

    fun subscriptionsOf(uid: String): Map<String, Map<String, Any>> = subscriptions[uid]?.toMap().orEmpty()

    fun preferencesOf(uid: String): Map<String, Any>? = preferences[uid]

    fun storeSubscription(uid: String, id: String, data: Map<String, Any>) {
        subscriptions.getOrPut(uid) { mutableMapOf() }[id] = data
        devices.forEach { it.deliver(uid) }
    }

    fun storePreferences(uid: String, data: Map<String, Any>) {
        preferences[uid] = data
        devices.forEach { it.deliver(uid) }
    }
}

/** One phone's view of the cloud: its signed-in account, live listeners, and connection. */
internal class FakeDevice(private val cloud: FakeCloud) : CloudBackend {
    private class Listener(val uid: String, val onChange: (fromCache: Boolean) -> Unit)

    override var currentUid: String? = null
        private set

    /** How many upcoming listener registrations report an error instead of connecting. */
    var failingListens = 0

    private var online = true
    private val accountListeners = mutableListOf<(String?) -> Unit>()
    private val listeners = mutableListOf<Listener>()
    private val queuedWrites = mutableListOf<() -> Unit>()

    fun signIn(uid: String?) {
        currentUid = uid
        accountListeners.toList().forEach { it(uid) }
    }

    fun goOffline() {
        online = false
        listeners.toList().forEach { it.onChange(true) }
    }

    fun goOnline() {
        online = true
        queuedWrites.toList().also { queuedWrites.clear() }.forEach { it() }
        listeners.toList().forEach { it.onChange(false) }
    }

    fun deliver(uid: String) {
        if (online) listeners.filter { it.uid == uid }.forEach { it.onChange(false) }
    }

    override fun addAccountListener(onChange: (uid: String?) -> Unit) {
        accountListeners += onChange
        onChange(currentUid) // Firebase reports the current account as soon as a listener is added.
    }

    override fun listenSubscriptions(
        uid: String,
        onChange: (documents: Map<String, Map<String, Any>>, fromCache: Boolean) -> Unit,
        onError: (Exception) -> Unit,
    ): SyncRegistration = listen(uid, onError) { fromCache -> onChange(cloud.subscriptionsOf(uid), fromCache) }

    override fun listenPreferences(
        uid: String,
        onChange: (data: Map<String, Any>?, fromCache: Boolean) -> Unit,
        onError: (Exception) -> Unit,
    ): SyncRegistration = listen(uid, onError) { fromCache -> onChange(cloud.preferencesOf(uid), fromCache) }

    override fun writeSubscription(
        uid: String,
        id: String,
        data: Map<String, Any>,
        onComplete: (success: Boolean) -> Unit,
    ) = send {
        cloud.storeSubscription(uid, id, data)
        onComplete(true)
    }

    override fun writePreferences(uid: String, data: Map<String, Any>, onComplete: (success: Boolean) -> Unit) =
        send {
            cloud.storePreferences(uid, data)
            onComplete(true)
        }

    private fun listen(
        uid: String,
        onError: (Exception) -> Unit,
        onChange: (fromCache: Boolean) -> Unit,
    ): SyncRegistration {
        if (failingListens > 0) {
            failingListens--
            onError(IllegalStateException("The listener failed."))
            return SyncRegistration {}
        }
        val listener = Listener(uid, onChange)
        listeners += listener
        if (online) onChange(false)
        return SyncRegistration { listeners -= listener }
    }

    // Like Firestore, writes made offline wait and are sent once the connection returns.
    private fun send(write: () -> Unit) {
        if (online) write() else queuedWrites += write
    }
}
