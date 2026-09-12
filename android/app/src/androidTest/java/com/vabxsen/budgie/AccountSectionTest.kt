package com.vabxsen.budgie

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vabxsen.budgie.auth.AccountProfile
import com.vabxsen.budgie.auth.AccountState
import com.vabxsen.budgie.data.SyncState
import com.vabxsen.budgie.data.SyncStatus
import com.vabxsen.budgie.ui.AccountSection
import com.vabxsen.budgie.ui.BudgieTheme
import com.vabxsen.budgie.domain.Appearance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSectionTest {
    @get:Rule val ui = createComposeRule()

    @Test fun signedOutOffersGoogleAndExplainsAccountSync() {
        var attempts = 0
        ui.setContent { BudgieTheme(Appearance.LIGHT) { AccountSection(AccountState(), { attempts++ }, {}) } }
        ui.onNodeWithText("Sign in with Google").performClick()
        assertEquals(1, attempts)
        ui.onNodeWithText("Sign in with Google to keep this collection available across your Android devices.").assertIsDisplayed()
    }

    @Test fun busyStatePreventsDuplicateSignIn() {
        ui.setContent { BudgieTheme(Appearance.LIGHT) { AccountSection(AccountState(busy = true), {}, {}) } }
        ui.onNodeWithText("Signing in…").assertIsNotEnabled()
    }

    @Test fun signOutRequiresConfirmationAndCanBeCancelled() {
        var signedOut = 0
        val state = AccountState(profile = AccountProfile("Budgie tester", "tester@example.com"))
        ui.setContent { BudgieTheme(Appearance.LIGHT) { AccountSection(state, {}, { signedOut++ }) } }
        ui.onNodeWithText("tester@example.com").assertIsDisplayed()
        ui.onNodeWithText("Sign out").performClick()
        ui.onNodeWithText("Stay signed in").performClick()
        assertEquals(0, signedOut)
        ui.onNodeWithText("Sign out").performClick()
        ui.onNode(hasText("Sign out") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, signedOut)
    }

    @Test fun signedInAccountShowsCompletedSync() {
        val state = AccountState(profile = AccountProfile("Budgie tester", "tester@example.com"))
        ui.setContent {
            BudgieTheme(Appearance.LIGHT) {
                AccountSection(state, {}, {}, SyncState(SyncStatus.SYNCED, 42L))
            }
        }
        ui.onNodeWithText("Your collection is synced").assertIsDisplayed()
        ui.onNodeWithText("Offline edits stay on this device and upload when you reconnect.").assertIsDisplayed()
    }

    @Test fun errorKeepsSignInAvailableForRetry() {
        val message = "Couldn’t connect. Check your internet connection and try again."
        ui.setContent { BudgieTheme(Appearance.LIGHT) { AccountSection(AccountState(message = message), {}, {}) } }
        ui.onNodeWithText(message).assertIsDisplayed()
        ui.onNodeWithText("Sign in with Google").assertIsEnabled()
    }
}
