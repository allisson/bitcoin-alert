package io.github.allisson.bitcoinalert.service

import io.github.allisson.bitcoinalert.domain.model.AlertDelivery
import io.github.allisson.bitcoinalert.domain.model.AlertDirection.ABOVE
import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import java.math.BigDecimal
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what the controller asks of the device. */
class FakeAlarmOutput(var focusDelayed: Boolean = false) : AlarmOutput {
    var sounding = false
    var vibrating = false
    var soundStarts = 0
    var focusRequests = 0
    var focusHeld = false
    var onFocusChange: ((Boolean) -> Unit)? = null

    override fun sound(on: Boolean) {
        if (on && !sounding) soundStarts++
        sounding = on
    }

    override fun vibrate(on: Boolean) {
        vibrating = on
    }

    override fun requestFocus(onChange: (gained: Boolean) -> Unit): Boolean {
        focusRequests++
        focusHeld = true
        onFocusChange = onChange
        return !focusDelayed
    }

    override fun abandonFocus() {
        focusHeld = false
    }
}

class AlarmControllerTest {
    private val output = FakeAlarmOutput()
    private val posted = mutableListOf<Pair<Long, AlarmNotice>>()

    private fun TestScope.controller(out: AlarmOutput = output) =
        AlarmController(out, backgroundScope) { entry, notice -> posted += entry.id to notice }

    private fun entry(id: Long) =
        AlertHistoryEntry(id, "a$id", BigDecimal("100.00"), BigDecimal("100.01"), ABOVE, 0, AlertDelivery.ALARM)

    @Test
    fun `two alarms ring one sound and post two notifications`() = runTest {
        val alarm = controller()
        alarm.ring(entry(1))
        alarm.ring(entry(2))
        assertTrue(output.sounding)
        assertTrue(output.vibrating)
        assertEquals(1, output.soundStarts)
        assertEquals(1, output.focusRequests)
        assertEquals(listOf(1L to AlarmNotice.Ringing, 2L to AlarmNotice.Ringing), posted)
        assertEquals(listOf(1L, 2L), alarm.ringing.value.map { it.id })
    }

    @Test
    fun `one Dismiss silences every alarm and reposts them as regular`() = runTest {
        val alarm = controller()
        alarm.ring(entry(1))
        alarm.ring(entry(2))
        posted.clear()
        alarm.dismiss()
        assertFalse(output.sounding)
        assertFalse(output.vibrating)
        assertFalse(output.focusHeld)
        assertEquals(emptyList<AlertHistoryEntry>(), alarm.ringing.value)
        assertEquals(listOf(1L to AlarmNotice.Dismissed, 2L to AlarmNotice.Dismissed), posted)
    }

    @Test
    fun `Dismiss with nothing ringing does nothing`() = runTest {
        controller().dismiss()
        assertEquals(emptyList<Pair<Long, AlarmNotice>>(), posted)
        assertFalse(output.focusHeld)
    }

    @Test
    fun `the timeout restarts on a new trigger and reposts the alarms as Missed`() = runTest {
        val alarm = controller()
        alarm.ring(entry(1))
        advanceTimeBy(9.minutes)
        alarm.ring(entry(2))
        advanceTimeBy(9.minutes)
        runCurrent()
        assertTrue(output.sounding)
        posted.clear()
        advanceTimeBy(1.minutes + 1.seconds)
        assertFalse(output.sounding)
        assertFalse(output.vibrating)
        assertEquals(listOf(1L to AlarmNotice.Missed, 2L to AlarmNotice.Missed), posted)
    }

    @Test
    fun `a Dismiss cancels the timeout`() = runTest {
        val alarm = controller()
        alarm.ring(entry(1))
        alarm.dismiss()
        posted.clear()
        advanceTimeBy(ALARM_TIMEOUT * 2)
        assertEquals(emptyList<Pair<Long, AlarmNotice>>(), posted)
    }

    @Test
    fun `a delayed audio focus vibrates without sound, then sounds on the gain`() = runTest {
        val delayed = FakeAlarmOutput(focusDelayed = true)
        val alarm = controller(delayed)
        alarm.ring(entry(1))
        assertTrue(delayed.vibrating)
        assertFalse(delayed.sounding)
        delayed.onFocusChange!!(true)
        assertTrue(delayed.sounding)
        delayed.onFocusChange!!(false)
        assertFalse(delayed.sounding)
        assertTrue(delayed.vibrating)
    }

    @Test
    fun `a focus gain after Dismiss does not sound`() = runTest {
        val delayed = FakeAlarmOutput(focusDelayed = true)
        val alarm = controller(delayed)
        alarm.ring(entry(1))
        alarm.dismiss()
        delayed.onFocusChange!!(true)
        assertFalse(delayed.sounding)
    }
}
