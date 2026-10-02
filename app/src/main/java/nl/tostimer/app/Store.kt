package nl.tostimer.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private fun Context.prefs(name: String) = applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

/** Presets saved before ingredients existed had one filling; map them to the closest set. */
fun migrateFilling(old: String?): Set<Ingredient> = when (old) {
    "HAM" -> setOf(Ingredient.HAM, Ingredient.CHEESE)
    "TOMATO" -> setOf(Ingredient.CHEESE, Ingredient.OTHER)
    "LOADED" -> setOf(Ingredient.HAM, Ingredient.CHEESE, Ingredient.OTHER)
    else -> setOf(Ingredient.CHEESE)
}

/** User-editable presets, stored as JSON. */
object PresetStore {
    const val MAX = 6

    fun load(context: Context): List<Preset> {
        val json = context.prefs("presets").getString("list", null) ?: return Presets.defaults
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Preset(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    seconds = o.getInt("seconds"),
                    ingredients = if (o.has("ingredients")) decodeIngredients(o.getString("ingredients"))
                    else migrateFilling(o.optString("filling")),
                )
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: Presets.defaults
    }

    fun save(context: Context, presets: List<Preset>) {
        val arr = JSONArray()
        presets.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("seconds", it.seconds).put("ingredients", it.ingredients.encode()))
        }
        context.prefs("presets").edit { putString("list", arr.toString()) }
    }
}

enum class AlarmSound(val label: String, val rawRes: Int?) {
    DINER_BELL("Diner bell", R.raw.sound_diner_bell),
    SIZZLE("Sizzle & ping", R.raw.sound_sizzle),
    TOASTER_POP("Toaster pop", R.raw.sound_toaster_pop),
    SYSTEM("Phone alarm", null),
}

object AppSettings {
    private fun p(c: Context) = c.prefs("settings")

    fun doneness(c: Context): Doneness =
        runCatching { Doneness.valueOf(p(c).getString("doneness", "")!!) }.getOrDefault(Doneness.GOLDEN)

    fun setDoneness(c: Context, d: Doneness) = p(c).edit { putString("doneness", d.name) }

    fun sound(c: Context): AlarmSound =
        runCatching { AlarmSound.valueOf(p(c).getString("sound", "")!!) }.getOrDefault(AlarmSound.DINER_BELL)

    fun setSound(c: Context, s: AlarmSound) = p(c).edit { putString("sound", s.name) }

    /** The preset last used, so the app opens on what you usually make. */
    fun lastPresetId(c: Context): String? = p(c).getString("lastPreset", null)

    fun setLastPresetId(c: Context, id: String) = p(c).edit { putString("lastPreset", id) }

    /** The un-scaled time last used with that preset. */
    fun lastBaseSeconds(c: Context): Int = p(c).getInt("lastBase", Presets.defaults.first().seconds)

    fun setLastBaseSeconds(c: Context, s: Int) = p(c).edit { putInt("lastBase", s) }
}

data class TostiRecord(val finishedAt: Long, val seconds: Int, val overcooked: Boolean, val label: String)

object StatsStore {
    private const val CAP = 500

    fun all(context: Context): List<TostiRecord> {
        val json = context.prefs("stats").getString("records", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TostiRecord(o.getLong("t"), o.getInt("s"), o.getBoolean("o"), o.getString("l").let { if (it == "Your") "Custom" else it })
            }
        }.getOrDefault(emptyList())
    }

    fun record(context: Context, r: TostiRecord) {
        val kept = (all(context) + r).takeLast(CAP)
        val arr = JSONArray()
        kept.forEach { arr.put(JSONObject().put("t", it.finishedAt).put("s", it.seconds).put("o", it.overcooked).put("l", it.label)) }
        context.prefs("stats").edit { putString("records", arr.toString()) }
    }
}

data class Stats(
    val count: Int,
    val averageSeconds: Int,
    val thisWeek: Int,
    /** Tostis in a row, newest first, that weren't overcooked. */
    val perfectStreak: Int,
    val daysSinceOvercooked: Int?,
    val favourite: String?,
)

fun computeStats(records: List<TostiRecord>, now: Long = System.currentTimeMillis()): Stats {
    val weekAgo = now - TimeUnit.DAYS.toMillis(7)
    val lastBurnt = records.lastOrNull { it.overcooked }
    return Stats(
        count = records.size,
        averageSeconds = if (records.isEmpty()) 0 else records.sumOf { it.seconds } / records.size,
        thisWeek = records.count { it.finishedAt >= weekAgo },
        perfectStreak = records.asReversed().takeWhile { !it.overcooked }.size,
        daysSinceOvercooked = lastBurnt?.let { TimeUnit.MILLISECONDS.toDays(now - it.finishedAt).toInt() },
        favourite = records.groupingBy { it.label }.eachCount().maxByOrNull { it.value }?.key,
    )
}
