package nl.tostimer.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CH_RUNNING = "running"
    private const val CH_ALERT = "alert"
    private const val CH_ALARM = "alarm"

    const val ID_RUNNING = 1
    const val ID_ALERT = 2
    const val ID_ALARM = 3

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, context.getString(R.string.channel_running), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, context.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH),
        )
        // The alarm sound is played by AlarmService, so this channel itself stays silent.
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notify(context: Context, id: Int, n: Notification) {
        if (canNotify(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun action(context: Context, action: String, seconds: Int? = null): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode() + (seconds ?: 0),
            Intent(context, TimerReceiver::class.java).setAction(action).apply {
                if (seconds != null) putExtra(TimerManager.EXTRA_SECONDS, seconds)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Live countdown rendered by the system itself, so it needs no service or updates from us. */
    fun showRunning(context: Context, s: TimerState.Running) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_tosti)
            .setContentTitle("${s.label} tosti is cooking")
            .setContentText("Time left")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(s.endAt)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(context))
            .addAction(0, "+30s", action(context, TimerManager.ACTION_ADD_TIME, 30))
            .addAction(0, "Stop", action(context, TimerManager.ACTION_STOP))
            .build()
        notify(context, ID_RUNNING, n)
    }

    fun showHeadsUp(context: Context, s: TimerState.Running) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_tosti)
            .setContentTitle("Almost done!")
            .setContentText("Your ${s.label} tosti is nearly ready")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setTimeoutAfter(60_000)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        notify(context, ID_ALERT, n)
    }

    /** Foreground notification for [AlarmService]; also launches [AlarmActivity] full screen. */
    fun buildAlarm(context: Context, label: String, escalated: Boolean): Notification {
        ensureChannels(context)
        val fullScreen = PendingIntent.getActivity(
            context, 1, Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_tosti)
            .setContentTitle(if (escalated) "Your tosti is getting cold!" else "Your tosti is ready!")
            .setContentText(if (escalated) "$label tosti has been waiting for a while" else "$label tosti is done")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Got it", action(context, TimerManager.ACTION_STOP))
            .addAction(0, "+1 min", action(context, TimerManager.ACTION_ADD_TIME, 60))
            .build()
    }

    fun updateAlarm(context: Context, n: Notification) = notify(context, ID_ALARM, n)

    fun cancelRunning(context: Context) = NotificationManagerCompat.from(context).cancel(ID_RUNNING)
    fun cancelAlert(context: Context) = NotificationManagerCompat.from(context).cancel(ID_ALERT)
    fun cancelAll(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ID_RUNNING); nm.cancel(ID_ALERT); nm.cancel(ID_ALARM)
    }
}
