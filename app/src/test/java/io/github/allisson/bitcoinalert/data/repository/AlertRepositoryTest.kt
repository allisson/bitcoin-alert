package io.github.allisson.bitcoinalert.data.repository

import androidx.room.Room
import io.github.allisson.bitcoinalert.data.database.AppDatabase
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.ABOVE
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.BELOW
import io.github.allisson.bitcoinalert.domain.model.AlertState
import io.github.allisson.bitcoinalert.domain.model.PriceAlert
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AlertRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private var now = 1_000L
    private val databases = mutableListOf<AppDatabase>()

    private fun inMemory() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries().build().also(databases::add)

    private fun onFile() = Room.databaseBuilder(context, AppDatabase::class.java, "test.db")
        .allowMainThreadQueries().build().also(databases::add)

    private fun repository(db: AppDatabase = inMemory()) = AlertRepository(db.alertDao()) { now }

    @After
    fun close() = databases.forEach { it.close() }

    private suspend fun AlertRepository.find(id: String) = alerts.first().single { it.id == id }

    private fun price(text: String) = BigDecimal(text)

    @Test
    fun `create stores an Active alert in cycle 0 with the target at scale 2`() = runBlocking {
        val repo = repository()
        val created = repo.create(ABOVE, price("100000"))
        val stored = repo.find(created.id)
        assertEquals(AlertState.Active, stored.state)
        assertEquals(0, stored.activationCycle)
        assertEquals(1_000L, stored.createdAt)
        assertEquals("100000.00", stored.targetPrice.toPlainString())
    }

    @Test
    fun `alerts are ordered createdAt DESC, id DESC`() = runBlocking {
        val repo = repository()
        val first = repo.create(ABOVE, price("1"))
        now = 2_000L
        val tieA = repo.create(BELOW, price("2"))
        val tieB = repo.create(BELOW, price("3"))
        val (high, low) = listOf(tieA.id, tieB.id).sortedDescending()
        assertEquals(listOf(high, low, first.id), repo.alerts.first().map { it.id })
    }

    @Test
    fun `trigger marks the alert Triggered and writes the history entry`() = runBlocking {
        val repo = repository()
        val alert = repo.create(ABOVE, price("100000.5"))
        now = 5_000L
        val historyId = repo.trigger(alert, price("100000.51000000"))
        assertNotNull(historyId)
        val stored = repo.find(alert.id)
        assertEquals(AlertState.Triggered, stored.state)
        assertEquals(5_000L, stored.triggeredAt)
        val entry = repo.history.first().single()
        assertEquals(historyId, entry.id)
        assertEquals(alert.id, entry.alertId)
        assertEquals("100000.50", entry.targetPrice.toPlainString())
        assertEquals("100000.51", entry.observedPrice.toPlainString())
        assertEquals(ABOVE, entry.direction)
        assertEquals(5_000L, entry.triggeredAt)
    }

    @Test
    fun `a second trigger of the same activation changes nothing`() = runBlocking {
        val repo = repository()
        val alert = repo.create(BELOW, price("90000"))
        assertNotNull(repo.trigger(alert, price("89999.99")))
        assertNull(repo.trigger(alert, price("89999.98")))
        assertEquals(1, repo.history.first().size)
    }

    @Test
    fun `a trigger from a replaced cycle changes nothing`() = runBlocking {
        val repo = repository()
        val alert = repo.create(BELOW, price("90000"))
        repo.disable(alert.id)
        repo.activate(alert.id)
        assertNull(repo.trigger(alert, price("89999.99")))
        assertEquals(AlertState.Active, repo.find(alert.id).state)
        assertEquals(0, repo.history.first().size)
    }

    @Test
    fun `a Disabled alert does not trigger`() = runBlocking {
        val repo = repository()
        val alert = repo.create(ABOVE, price("100000"))
        repo.disable(alert.id)
        assertNull(repo.trigger(alert, price("100000.01")))
    }

    @Test
    fun `disable keeps triggeredAt`() = runBlocking {
        val repo = repository()
        val alert = repo.create(ABOVE, price("100000"))
        repo.trigger(alert, price("100000.01"))
        repo.disable(alert.id)
        val stored = repo.find(alert.id)
        assertEquals(AlertState.Disabled, stored.state)
        assertEquals(1_000L, stored.triggeredAt)
    }

    @Test
    fun `enabling and reactivating start a new cycle`() = runBlocking {
        val repo = repository()
        val alert = repo.create(ABOVE, price("100000"))
        repo.disable(alert.id)
        repo.activate(alert.id)
        assertEquals(1, repo.find(alert.id).activationCycle)

        val enabled = repo.find(alert.id)
        repo.trigger(enabled, price("100000.01"))
        repo.activate(alert.id)
        val reactivated = repo.find(alert.id)
        assertEquals(AlertState.Active, reactivated.state)
        assertNull(reactivated.triggeredAt)
        assertEquals(2, reactivated.activationCycle)
        assertNotNull(repo.trigger(reactivated, price("100000.02")))
    }

    @Test
    fun `activating an Active alert keeps its cycle`() = runBlocking {
        val repo = repository()
        val alert = repo.create(ABOVE, price("100000"))
        repo.activate(alert.id)
        assertEquals(0, repo.find(alert.id).activationCycle)
    }

    @Test
    fun `deleting an alert keeps its history`() = runBlocking {
        val repo = repository()
        val alert = repo.create(ABOVE, price("100000"))
        repo.trigger(alert, price("100000.01"))
        repo.delete(alert.id)
        assertEquals(emptyList<PriceAlert>(), repo.alerts.first())
        assertEquals(alert.id, repo.history.first().single().alertId)
    }

    @Test
    fun `history is ordered triggeredAt DESC, id DESC`() = runBlocking {
        val repo = repository()
        val a = repo.create(ABOVE, price("1"))
        val b = repo.create(ABOVE, price("2"))
        val c = repo.create(ABOVE, price("3"))
        now = 2_000L
        val older = repo.trigger(a, price("1.01"))!!
        now = 3_000L
        val sameTickFirst = repo.trigger(b, price("2.01"))!!
        val sameTickSecond = repo.trigger(c, price("3.01"))!!
        assertEquals(listOf(sameTickSecond, sameTickFirst, older), repo.history.first().map { it.id })
    }

    @Test
    fun `alerts and history survive a database reopen`() = runBlocking {
        context.deleteDatabase("test.db")
        val first = onFile()
        val alert = repository(first).create(ABOVE, price("100000"))
        repository(first).trigger(alert, price("100000.01"))
        repository(first).create(BELOW, price("90000"))
        first.close()

        val reopened = repository(onFile())
        assertEquals(2, reopened.alerts.first().size)
        assertEquals(AlertState.Triggered, reopened.find(alert.id).state)
        assertEquals("100000.01", reopened.history.first().single().observedPrice.toPlainString())
    }
}
