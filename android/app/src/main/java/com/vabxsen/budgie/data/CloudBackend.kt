package com.vabxsen.budgie.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges

internal fun interface SyncRegistration {
    fun remove()
}

/** The account and cloud operations that sync needs; unit tests use an in-memory stand-in. */
internal interface CloudBackend {
    val currentUid: String?

    fun addAccountListener(onChange: (uid: String?) -> Unit)

    fun listenSubscriptions(
        uid: String,
        onChange: (documents: Map<String, Map<String, Any>>, fromCache: Boolean) -> Unit,
        onError: (Exception) -> Unit,
    ): SyncRegistration

    fun listenPreferences(
        uid: String,
        onChange: (data: Map<String, Any>?, fromCache: Boolean) -> Unit,
        onError: (Exception) -> Unit,
    ): SyncRegistration

    fun writeSubscription(uid: String, id: String, data: Map<String, Any>, onComplete: (success: Boolean) -> Unit)

    fun writePreferences(uid: String, data: Map<String, Any>, onComplete: (success: Boolean) -> Unit)
}

internal class FirebaseBackend(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CloudBackend {
    override val currentUid: String?
        get() = auth.currentUser?.uid

    override fun addAccountListener(onChange: (uid: String?) -> Unit) {
        auth.addAuthStateListener { onChange(it.currentUser?.uid) }
    }

    // Metadata changes are included so listeners also report when the server confirms cached data
    // and when the connection drops.
    override fun listenSubscriptions(
        uid: String,
        onChange: (documents: Map<String, Map<String, Any>>, fromCache: Boolean) -> Unit,
        onError: (Exception) -> Unit,
    ): SyncRegistration {
        val registration =
            subscriptions(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null) {
                    onChange(snapshot.documents.associate { it.id to it.data.orEmpty() }, snapshot.metadata.isFromCache)
                }
            }
        return SyncRegistration { registration.remove() }
    }

    override fun listenPreferences(
        uid: String,
        onChange: (data: Map<String, Any>?, fromCache: Boolean) -> Unit,
        onError: (Exception) -> Unit,
    ): SyncRegistration {
        val registration =
            preferences(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) onError(error)
                else if (snapshot != null) onChange(snapshot.data, snapshot.metadata.isFromCache)
            }
        return SyncRegistration { registration.remove() }
    }

    override fun writeSubscription(
        uid: String,
        id: String,
        data: Map<String, Any>,
        onComplete: (success: Boolean) -> Unit,
    ) {
        subscriptions(uid).document(id).set(data).addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    override fun writePreferences(uid: String, data: Map<String, Any>, onComplete: (success: Boolean) -> Unit) {
        preferences(uid).set(data).addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    private fun subscriptions(uid: String) =
        firestore.collection("users").document(uid).collection("subscriptions")

    private fun preferences(uid: String) =
        firestore.collection("users").document(uid).collection("settings").document("preferences")
}
