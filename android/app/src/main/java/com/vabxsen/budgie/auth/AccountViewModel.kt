package com.vabxsen.budgie.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.vabxsen.budgie.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

data class AccountProfile(val name: String, val email: String)

data class AccountState(
    val profile: AccountProfile? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class AccountViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val mutableState = MutableStateFlow(AccountState(profile = auth.currentUser.toProfile()))
    val state = mutableState.asStateFlow()
    private val listener = FirebaseAuth.AuthStateListener { firebase ->
        mutableState.update { it.copy(profile = firebase.currentUser.toProfile()) }
    }

    init { auth.addAuthStateListener(listener) }

    // The caller's composition scope cancels the account picker when its Activity is destroyed.
    // Never retain an Activity or an ID token in the ViewModel or saved app data.
    suspend fun signIn(activity: Activity) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        try {
            val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val credential = result.credential
            check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            mutableState.update { it.copy(message = "You’re signed in. Budgie is connecting your collection.") }
        } catch (_: GetCredentialCancellationException) {
            // Dismissing Google's picker is a normal user choice.
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: NoCredentialException) {
            mutableState.update { it.copy(message = "Add a Google account in Android Settings, then try again.") }
        } catch (_: FirebaseNetworkException) {
            mutableState.update { it.copy(message = "Couldn’t connect. Check your internet connection and try again.") }
        } catch (_: FirebaseAuthInvalidUserException) {
            mutableState.update { it.copy(message = "This account is unavailable. Try another Google account.") }
        } catch (_: Exception) {
            mutableState.update { it.copy(message = "Google sign-in couldn’t finish. Check your connection and Google Play services, then try again.") }
        } finally {
            mutableState.update { it.copy(busy = false) }
        }
    }

    suspend fun signOut(activity: Activity) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        auth.signOut()
        try {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
            mutableState.update { it.copy(message = "You’re signed out. Your account collection remains saved on this device.") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(message = "You’re signed out. Google may suggest your previous account next time.") }
        } finally {
            mutableState.update { it.copy(busy = false) }
        }
    }

    override fun onCleared() { auth.removeAuthStateListener(listener) }
}

private fun FirebaseUser?.toProfile(): AccountProfile? = this?.let {
    AccountProfile(displayName?.takeIf(String::isNotBlank) ?: "Your Google account", email.orEmpty())
}
