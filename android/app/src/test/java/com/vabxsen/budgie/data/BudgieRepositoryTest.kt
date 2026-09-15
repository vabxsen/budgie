package com.vabxsen.budgie.data

import com.vabxsen.budgie.domain.BudgieCollection
import com.vabxsen.budgie.domain.Preferences
import com.vabxsen.budgie.domain.Subscription
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Simulated phones that share one in-memory cloud. */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgieRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val cloud = FakeCloud()
    private var time = 1_000_000L

    private class MemoryGuestClaim : GuestClaim {
        override var claimed = false

        override fun markClaimed() {
            claimed = true
        }
    }

    private fun TestScope.phone(device: FakeDevice) =
        BudgieRepository(
            folder.newFolder(),
            MemoryGuestClaim(),
            device,
            StandardTestDispatcher(testScheduler),
            clock = { time++ },
        )

    private suspend fun TestScope.signedInPhone(uid: String): BudgieRepository =
        phone(cloud.device().apply { signIn(uid) }).also {
            it.load()
            advanceUntilIdle()
        }

    private fun BudgieRepository.collection() = state.value!!.getOrThrow()

    private fun BudgieRepository.ids() = collection().subscriptions.map { it.id }.toSet()

    private fun subscription(id: String, name: String, priceMinor: Long) =
        Subscription(
            id = id,
            name = name,
            priceMinor = priceMinor,
            anchorDate = LocalDate.of(2026, 9, 15),
            createdDate = LocalDate.of(2026, 9, 1),
        )

    @Test
    fun secondPhoneKeepsTheAccountSettingsAndSkipsDuplicates() = runTest {
        val firstDevice = cloud.device()
        val first = phone(firstDevice)
        first.load()
        first.update {
            BudgieCollection(
                listOf(subscription("a", "Netflix", 64900)),
                Preferences(budgetMinor = 500000, onboarded = true),
            )
        }
        firstDevice.signIn("user")
        advanceUntilIdle()

        val secondDevice = cloud.device()
        val second = phone(secondDevice)
        second.load()
        second.update {
            BudgieCollection(
                listOf(subscription("b", "netflix ", 64900), subscription("c", "Gym", 150000)),
                Preferences(onboarded = true),
            )
        }
        secondDevice.signIn("user")
        advanceUntilIdle()

        assertEquals(setOf("a", "c"), second.ids())
        assertEquals(setOf("a", "c"), first.ids())
        assertEquals(500000L, second.collection().preferences.budgetMinor)
        assertEquals(500000L, first.collection().preferences.budgetMinor)
        assertEquals(SyncStatus.SYNCED, second.syncState.value.status)
    }

    @Test
    fun editsAndDeletionsReachTheOtherPhone() = runTest {
        val first = signedInPhone("user")
        val second = signedInPhone("user")

        first.update { it.copy(subscriptions = listOf(subscription("n", "Netflix", 64900))) }
        advanceUntilIdle()
        assertEquals(setOf("n"), second.ids())

        second.update { c -> c.copy(subscriptions = c.subscriptions.map { it.copy(priceMinor = 79900) }) }
        advanceUntilIdle()
        assertEquals(79900L, first.collection().subscriptions.single().priceMinor)

        first.update { it.copy(subscriptions = emptyList()) }
        advanceUntilIdle()
        assertTrue(second.ids().isEmpty())
        assertEquals(true, cloud.subscriptionsOf("user")["n"]?.get("deleted"))
    }

    @Test
    fun eachAccountKeepsItsOwnCollection() = runTest {
        val device = cloud.device()
        val phone = phone(device)
        phone.load()
        phone.update { it.copy(subscriptions = listOf(subscription("guest", "Netflix", 64900))) }

        device.signIn("alice")
        advanceUntilIdle()
        phone.update { c -> c.copy(subscriptions = c.subscriptions + subscription("gym", "Gym", 150000)) }
        advanceUntilIdle()
        assertEquals(setOf("guest", "gym"), cloud.subscriptionsOf("alice").keys)

        device.signIn(null)
        advanceUntilIdle()
        assertEquals(setOf("guest"), phone.ids())
        assertEquals(SyncStatus.SIGNED_OUT, phone.syncState.value.status)

        device.signIn("bob")
        advanceUntilIdle()
        assertTrue(phone.ids().isEmpty())
        assertTrue(cloud.subscriptionsOf("bob").isEmpty())

        device.signIn("alice")
        advanceUntilIdle()
        assertEquals(setOf("guest", "gym"), phone.ids())
    }

    @Test
    fun reminderPermissionStaysOnEachPhone() = runTest {
        val first = signedInPhone("user")
        val second = signedInPhone("user")

        first.update { it.copy(preferences = it.preferences.copy(notifications = true, reminderDays = 7)) }
        advanceUntilIdle()

        assertEquals(7, second.collection().preferences.reminderDays)
        assertFalse(second.collection().preferences.notifications)
        assertTrue(first.collection().preferences.notifications)
    }

    @Test
    fun offlineEditsWaitAndSyncAfterReconnecting() = runTest {
        val device = cloud.device().apply { signIn("user") }
        val phone = phone(device)
        phone.load()
        advanceUntilIdle()
        assertEquals(SyncStatus.SYNCED, phone.syncState.value.status)

        device.goOffline()
        phone.update { it.copy(subscriptions = listOf(subscription("g", "Gym", 150000))) }
        advanceUntilIdle()
        assertEquals(SyncStatus.OFFLINE, phone.syncState.value.status)
        assertTrue(cloud.subscriptionsOf("user").isEmpty())

        device.goOnline()
        advanceUntilIdle()
        assertEquals(SyncStatus.SYNCED, phone.syncState.value.status)
        assertEquals(setOf("g"), cloud.subscriptionsOf("user").keys)
    }

    @Test
    fun syncRestartsItselfAfterAnError() = runTest {
        val device = cloud.device().apply {
            signIn("user")
            failingListens = 1
        }
        val phone = phone(device)
        phone.load()
        assertEquals(SyncStatus.ERROR, phone.syncState.value.status)

        advanceUntilIdle()
        assertEquals(SyncStatus.SYNCED, phone.syncState.value.status)
    }
}
