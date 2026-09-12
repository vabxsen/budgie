package com.vabxsen.budgie.data

import com.vabxsen.budgie.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CollectionCodecTest {
    private val collection =
        BudgieCollection(
            ServiceCatalog.samples(LocalDate.of(2026, 9, 12)),
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
}
