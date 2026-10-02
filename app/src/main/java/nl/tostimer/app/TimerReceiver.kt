package nl.tostimer.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Entry point for alarms, notification actions and widget buttons. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerManager.ensureLoaded(context)
        when (intent.action) {
            TimerManager.ACTION_FINISH -> TimerManager.onFinished(context)
            TimerManager.ACTION_HEADS_UP -> TimerManager.onHeadsUp(context)
            TimerManager.ACTION_ADD_TIME ->
                TimerManager.addTime(context, intent.getIntExtra(TimerManager.EXTRA_SECONDS, 30))
            TimerManager.ACTION_STOP -> TimerManager.stop(context)
            TimerManager.ACTION_WIDGET_TICK -> TimerManager.onWidgetTick(context)
            TimerManager.ACTION_START ->
                intent.getStringExtra(TimerManager.EXTRA_PRESET_ID)?.let { TimerManager.startPreset(context, it) }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) TimerManager.restore(context)
    }
}
