package nl.tostimer.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import nl.tostimer.app.ui.TostiPalette
import nl.tostimer.app.ui.renderTostiBitmap

/**
 * Home-screen widget. The countdown is a RemoteViews Chronometer, so it ticks live. The tosti is a
 * bitmap drawn by the same code as the app's, redrawn in steps (see TimerManager.onWidgetTick).
 */
class TostiWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        TimerManager.ensureLoaded(context)
        val views = buildViews(context, TimerManager.state.value)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
        // A widget just got added mid-countdown: start its browning ticks.
        (TimerManager.state.value as? TimerState.Running)?.let { TimerManager.onWidgetTick(context) }
    }

    companion object {
        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TostiWidgetProvider::class.java))

        fun hasWidgets(context: Context) = ids(context).isNotEmpty()

        fun updateAll(context: Context) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            val views = buildViews(context, TimerManager.state.value)
            val manager = AppWidgetManager.getInstance(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context, state: TimerState): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_tosti)
            v.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            val plate = TostiPalette.Plate
            when (state) {
                TimerState.Idle -> {
                    val presets = PresetStore.load(context)
                    val doneness = AppSettings.doneness(context)
                    v.setImageViewBitmap(R.id.widget_image, renderTostiBitmap(0.1f, presets.first().ingredients, plate))
                    v.setTextViewText(R.id.widget_title, "Tostimer · ${doneness.label.lowercase()}")
                    v.setViewVisibility(R.id.widget_chronometer, View.GONE)
                    v.setViewVisibility(R.id.widget_status, View.VISIBLE)
                    v.setTextViewText(R.id.widget_status, "Ready to toast")
                    listOf(R.id.widget_btn_a, R.id.widget_btn_b).forEachIndexed { i, id ->
                        val p = presets.getOrNull(i)
                        if (p == null) {
                            v.setViewVisibility(id, View.GONE)
                        } else {
                            v.setViewVisibility(id, View.VISIBLE)
                            v.setTextViewText(id, "▶ ${p.name} ${formatDuration(doneness.apply(p.seconds) * 1000L)}")
                            v.setOnClickPendingIntent(id, startIntent(context, p))
                        }
                    }
                }
                is TimerState.Running -> {
                    val now = System.currentTimeMillis()
                    val total = state.totalMs.coerceAtLeast(1)
                    val elapsed = ((now - (state.endAt - total)).toFloat() / total).coerceIn(0f, 1f)
                    v.setImageViewBitmap(
                        R.id.widget_image,
                        renderTostiBitmap(state.brown * elapsed, state.ingredients, plate),
                    )
                    v.setTextViewText(R.id.widget_title, "${state.label} tosti")
                    v.setViewVisibility(R.id.widget_status, View.GONE)
                    v.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
                    val remaining = (state.endAt - now).coerceAtLeast(0)
                    v.setChronometerCountDown(R.id.widget_chronometer, true)
                    v.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime() + remaining, null, true)
                    buttons(context, v, "+30s", Notifier.action(context, TimerManager.ACTION_ADD_TIME, 30),
                        "Stop", Notifier.action(context, TimerManager.ACTION_STOP))
                }
                is TimerState.Finished -> {
                    v.setImageViewBitmap(R.id.widget_image, renderTostiBitmap(state.brown, state.ingredients, plate))
                    v.setTextViewText(R.id.widget_title, "${state.label} tosti")
                    v.setViewVisibility(R.id.widget_chronometer, View.GONE)
                    v.setViewVisibility(R.id.widget_status, View.VISIBLE)
                    v.setTextViewText(R.id.widget_status, "Tosti ready!")
                    buttons(context, v, "+1 min", Notifier.action(context, TimerManager.ACTION_ADD_TIME, 60),
                        "Got it", Notifier.action(context, TimerManager.ACTION_STOP))
                }
            }
            return v
        }

        private fun buttons(context: Context, v: RemoteViews, a: String, aIntent: PendingIntent, b: String, bIntent: PendingIntent) {
            v.setViewVisibility(R.id.widget_btn_a, View.VISIBLE)
            v.setTextViewText(R.id.widget_btn_a, a)
            v.setOnClickPendingIntent(R.id.widget_btn_a, aIntent)
            v.setViewVisibility(R.id.widget_btn_b, View.VISIBLE)
            v.setTextViewText(R.id.widget_btn_b, b)
            v.setOnClickPendingIntent(R.id.widget_btn_b, bIntent)
        }

        private fun startIntent(context: Context, p: Preset): PendingIntent =
            PendingIntent.getBroadcast(
                context, 1000 + p.id.hashCode(),
                Intent(context, TimerReceiver::class.java).setAction(TimerManager.ACTION_START)
                    .putExtra(TimerManager.EXTRA_PRESET_ID, p.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
