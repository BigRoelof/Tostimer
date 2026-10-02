package nl.tostimer.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat

/** Plays the looping alarm until the user reacts. Gets louder after [ESCALATE_AFTER_MS]. */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var label = "Tosti"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        label = intent?.getStringExtra(TimerManager.EXTRA_LABEL) ?: label
        ServiceCompat.startForeground(
            this, Notifier.ID_ALARM, Notifier.buildAlarm(this, label, escalated = false),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tostimer:alarm")
            .apply { acquire(MAX_RING_MS + 5_000) }
        startSound()
        startVibration()
        handler.postDelayed(::escalate, ESCALATE_AFTER_MS)
        handler.postDelayed(::giveUp, MAX_RING_MS)
        return START_NOT_STICKY
    }

    private fun startSound() {
        if (player != null) return
        val uri = soundUri(this, AppSettings.sound(this))
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            setVolume(0.5f, 0.5f)
            prepare()
            start()
        }
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    private fun escalate() {
        player?.setVolume(1f, 1f)
        Notifier.updateAlarm(this, Notifier.buildAlarm(this, label, escalated = true))
    }

    private fun giveUp() = TimerManager.stop(this)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        /** The chosen alarm sound: one of our bundled sounds, or the phone's alarm ringtone. */
        fun soundUri(context: Context, sound: AlarmSound): Uri =
            sound.rawRes?.let { Uri.parse("android.resource://${context.packageName}/$it") }
                ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        private const val ESCALATE_AFTER_MS = 2 * 60_000L
        private const val MAX_RING_MS = 10 * 60_000L

        fun start(context: Context, label: String) {
            val intent = Intent(context, AlarmService::class.java).putExtra(TimerManager.EXTRA_LABEL, label)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
