package io.github.allisson.bitcoinalert.service

import io.github.allisson.bitcoinalert.domain.model.AlertHistoryEntry
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Ringing with no Dismiss stops this long after the latest trigger (spec.md §2.6, Alarm delivery). */
val ALARM_TIMEOUT = 10.minutes

/** How an Alarm-delivery notification is posted. */
enum class AlarmNotice {
    /** Ongoing, with Dismiss and the full-screen intent. */
    Ringing,

    /** After Dismiss or a stop: a regular alert notification. */
    Dismissed,

    /** After the timeout: a regular notification titled `Missed: …`. */
    Missed,
}

/** The device side of ringing: sound, vibration and audio focus. */
interface AlarmOutput {
    fun sound(on: Boolean)

    fun vibrate(on: Boolean)

    /**
     * Requests the alarm audio focus with delayed gain. Returns `false` when the gain is delayed
     * (a call in progress); [onChange] then gets `true` on the gain, and `false` on a transient loss.
     */
    fun requestFocus(onChange: (gained: Boolean) -> Unit): Boolean

    fun abandonFocus()
}

/**
 * The ringing alarms, process-scoped so the alarm screen and the service share them: one sound for
 * all, one Dismiss for all, and a timeout restarted by each trigger (spec.md §2.6, Alarm delivery).
 * Call it from the main thread; the timeout runs in [scope].
 */
class AlarmController(
    private val output: AlarmOutput,
    private val scope: CoroutineScope,
    private val post: (AlertHistoryEntry, AlarmNotice) -> Unit,
) {
    private val _ringing = MutableStateFlow<List<AlertHistoryEntry>>(emptyList())

    /** The ringing alarms, in trigger order: the last one is the latest. */
    val ringing: StateFlow<List<AlertHistoryEntry>> = _ringing.asStateFlow()

    private var timeout: Job? = null

    fun ring(entry: AlertHistoryEntry) {
        val first = _ringing.value.isEmpty()
        _ringing.value += entry
        post(entry, AlarmNotice.Ringing)
        if (first) start()
        timeout?.cancel()
        timeout = scope.launch {
            delay(ALARM_TIMEOUT)
            silence(AlarmNotice.Missed)
        }
    }

    /** Dismiss, and stopping monitoring: silences every ringing alarm. */
    fun dismiss() = silence(AlarmNotice.Dismissed)

    private fun start() {
        output.vibrate(true)
        val granted = output.requestFocus { gained -> if (_ringing.value.isNotEmpty()) output.sound(gained) }
        if (granted) output.sound(true)
    }

    private fun silence(notice: AlarmNotice) {
        val entries = _ringing.value
        if (entries.isEmpty()) return
        timeout?.cancel()
        timeout = null
        _ringing.value = emptyList()
        output.sound(false)
        output.vibrate(false)
        output.abandonFocus()
        entries.forEach { post(it, notice) }
    }
}
