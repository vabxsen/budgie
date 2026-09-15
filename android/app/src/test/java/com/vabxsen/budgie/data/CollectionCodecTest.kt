package com.vabxsen.budgie.data

import com.vabxsen.budgie.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CollectionCodecTest {
    private val collection =
        BudgieCollection(
            listOf(Subscription(name = "Roundtrip", plan = "My plan", priceMinor = 12345, anchorDate = LocalDate.of(2026, 9, 12), notes = "My own note")),
            Preferences(onboarded = true, notifications = true),
        )

    @Test
    fun backupRoundTripsEveryField() {
        assertEquals(collection, CollectionCodec.decode(CollectionCodec.encode(collection)))
    }

    @Test
    fun unsupportedVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CollectionCodec.decode(
                CollectionCodec.encode(collection).replace("\"version\": 1", "\"version\": 999")
            )
        }
    }

    @Test
    fun duplicateIdentifiersAreRejected() {
        val duplicate =
            collection.copy(
                subscriptions =
                    listOf(collection.subscriptions.first(), collection.subscriptions.first())
            )
        assertThrows(IllegalArgumentException::class.java) {
            CollectionCodec.decode(CollectionCodec.encode(duplicate))
        }
    }

    @Test
    fun exportedTextDoesNotBecomeASpreadsheetFormula() {
        val data =
            collection.copy(
                subscriptions =
                    listOf(
                        collection.subscriptions
                            .first()
                            .copy(
                                name = "=HYPERLINK(\"https://example.invalid\")",
                                notes = "a,b\nwith \"quotes\"",
                            )
                    )
            )
        val csv = CollectionCodec.csv(data)
        assertTrue(csv.contains("\"'=HYPERLINK"))
        assertTrue(csv.contains("\"a,b\nwith \"\"quotes\"\"\""))
    }

    @Test
    fun malformedOrMissingFieldsAreRejected() {
        assertThrows(Exception::class.java) { CollectionCodec.decode("{}") }
    }
    @Test fun emptyCollectionRoundTripsWithoutAnInventedBudget() {
        val empty = CollectionCodec.decode(CollectionCodec.encode(BudgieCollection()))
        assertTrue(empty.subscriptions.isEmpty())
        assertEquals(0L, empty.preferences.budgetMinor)
    }

    @Test fun exportsReflectExpiredTrialsAndOmitArchivedRenewalDates() {
        val today = LocalDate.now()
        val entries = listOf(
            Subscription(name = "Expired trial", priceMinor = 1000,
                anchorDate = today.minusDays(1), status = SubscriptionStatus.TRIAL),
            Subscription(name = "Archived", priceMinor = 2000,
                anchorDate = today, status = SubscriptionStatus.ARCHIVED))
        val rows = CollectionCodec.csv(BudgieCollection(entries)).lines()
        assertTrue(rows[1].contains("\"Active\""))
        assertTrue(rows[2].contains("\"${today}\",\"\",\"Other\",\"Archived\""))
    }

    @Test fun oversizedBackupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CollectionCodec.decode("x".repeat(CollectionCodec.MAX_BACKUP_BYTES + 1))
        }
    }

    @Test fun csvStartsWithAByteOrderMarkForSpreadsheets() {
        assertTrue(CollectionCodec.csv(collection).startsWith("${Char(0xFEFF)}Service,"))
    }

}
