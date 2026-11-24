package io.github.allisson.bitcoinalert.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

private const val TAG = "DeviceAlarmOutput"

private val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** On for 800 ms, off for 800 ms, repeated from index 0. */
private val VIBRATION = VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0)

/**
 * The system default alarm sound in a loop on the alarm stream, which plays in silent mode and
 * through Do Not Disturb by default, and a looping vibration (spec.md §2.6, Alarm delivery).
 */
class DeviceAlarmOutput(private val context: Context) : AlarmOutput {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    override fun sound(on: Boolean) {
        if (!on) {
            player?.release()
            player = null
            return
        }
        if (player != null) return
        // No alarm sound set on the device: the ringtone still wakes the user.
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        val created = MediaPlayer()
        player = try {
            created.apply {
                setAudioAttributes(ALARM_ATTRIBUTES)
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // IOException, IllegalArgumentException, SecurityException…: the vibration goes on.
            Log.w(TAG, "Alarm sound unavailable", e)
            created.release()
            null
        }
    }

    override fun vibrate(on: Boolean) {
        if (!on) return vibrator.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(VIBRATION, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION, ALARM_ATTRIBUTES)
        }
    }

    override fun requestFocus(onChange: (gained: Boolean) -> Unit): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(ALARM_ATTRIBUTES)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> onChange(true)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onChange(false)
                }
            }
            .build()
        focus = request
        // A refused request still rings: waking the user comes first.
        return audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_DELAYED
    }

    override fun abandonFocus() {
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
    }
}
