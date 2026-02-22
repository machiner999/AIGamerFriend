package com.example.aigamerfriend.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val counter = AtomicInteger(0)

    private fun runMemoryTest(block: suspend TestScope.(MemoryStore) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val testFile = java.io.File(tmpFolder.root, "test_${counter.getAndIncrement()}.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher() + Job()),
                produceFile = { testFile },
            )
            val store = MemoryStore(dataStore)
            block(store)
        }

    @Test
    fun `empty state returns null`() = runMemoryTest { store ->
        assertNull(store.getFormattedSummaries())
    }

    @Test
    fun `add and get summary`() = runMemoryTest { store ->
        store.addSummary("Player was playing Mario Kart")

        val result = store.getFormattedSummaries()
        assertNotNull(result)
        assertTrue(result!!.contains("Player was playing Mario Kart"))
    }

    @Test
    fun `multiple summaries are returned`() = runMemoryTest { store ->
        store.addSummary("First session")
        store.addSummary("Second session")

        val result = store.getFormattedSummaries()
        assertNotNull(result)
        assertTrue(result!!.contains("First session"))
        assertTrue(result.contains("Second session"))
    }

    @Test
    fun `max capacity drops oldest`() = runMemoryTest { store ->
        for (i in 1..12) {
            store.addSummary("Session $i")
        }

        val result = store.getFormattedSummaries()
        assertNotNull(result)
        val lines = result!!.split("\n")
        assertEquals(10, lines.size)
        assertTrue(!result.contains("Session 1]"))
        assertTrue(!result.contains("Session 2]"))
        assertTrue(result.contains("Session 3"))
        assertTrue(result.contains("Session 12"))
    }

    @Test
    fun `clear removes all summaries`() = runMemoryTest { store ->
        store.addSummary("Some session")
        assertNotNull(store.getFormattedSummaries())

        store.clear()

        assertNull(store.getFormattedSummaries())
    }

    @Test
    fun `long summary is truncated`() = runMemoryTest { store ->
        val longText = "A".repeat(300)
        store.addSummary(longText)

        val result = store.getFormattedSummaries()
        assertNotNull(result)
        assertTrue(result!!.contains("A".repeat(200) + "..."))
    }
}
