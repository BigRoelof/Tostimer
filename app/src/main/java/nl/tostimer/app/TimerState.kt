package nl.tostimer.app

import kotlin.math.roundToInt

/** What goes between the bread. A tosti can have any combination; each one shows in the illustration. */
enum class Ingredient(val label: String) {
    HAM("Ham"),
    CHEESE("Cheese"),
    PESTO("Pesto"),
    OTHER("Other"),
}

fun Set<Ingredient>.describe(): String =
    if (isEmpty()) "plain" else sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase() }

fun Set<Ingredient>.encode(): String = sortedBy { it.ordinal }.joinToString(",") { it.name }

fun decodeIngredients(csv: String?): Set<Ingredient> =
    csv.orEmpty().split(",").mapNotNull { name -> Ingredient.entries.firstOrNull { it.name == name } }.toSet()

/** How toasted you like it: scales the time, and sets how brown the illustration ends up. */
enum class Doneness(val label: String, val timeFactor: Float, val brown: Float) {
    PALE("Pale", 0.85f, 0.55f),
    GOLDEN("Golden", 1f, 0.8f),
    DARK("Dark", 1.2f, 1f),
    ;

    /** Scaled time in seconds, rounded to 5 s. */
    fun apply(baseSeconds: Int): Int = ((baseSeconds * timeFactor / 5f).roundToInt() * 5).coerceAtLeast(15)
}

data class Preset(val id: String, val name: String, val seconds: Int, val ingredients: Set<Ingredient>)

object Presets {
    val defaults = listOf(
        Preset("classic", "Classic", 210, setOf(Ingredient.HAM, Ingredient.CHEESE)),
        Preset("loaded", "Loaded", 300, setOf(Ingredient.HAM, Ingredient.CHEESE, Ingredient.PESTO)),
    )
}

sealed interface TimerState {
    data object Idle : TimerState

    /** [endAt] is wall-clock millis (System.currentTimeMillis) when the alarm fires. */
    data class Running(
        val endAt: Long,
        val totalMs: Long,
        val label: String,
        val ingredients: Set<Ingredient>,
        val brown: Float,
    ) : TimerState

    /** The timer ran out and the alarm is (or was) ringing. */
    data class Finished(
        val label: String,
        val ingredients: Set<Ingredient>,
        val brown: Float,
        val finishedAt: Long,
        val totalMs: Long,
    ) : TimerState
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
