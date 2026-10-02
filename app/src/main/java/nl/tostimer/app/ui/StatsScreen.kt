package nl.tostimer.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tostimer.app.Stats
import nl.tostimer.app.TostiRecord
import nl.tostimer.app.computeStats
import nl.tostimer.app.formatDuration

@Composable
fun StatsScreen(records: List<TostiRecord>, onBack: () -> Unit) {
    val stats = computeStats(records)
    ScreenFrame("Tosti stats", onBack) {
        if (records.isEmpty()) {
            Spacer(Modifier.height(48.dp))
            Text("No tostis yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Make your first one and it shows up here.",
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            StreakCard(stats)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Made", stats.count.toString(), Modifier.weight(1f))
                StatTile("This week", stats.thisWeek.toString(), Modifier.weight(1f))
                StatTile("Average", formatDuration(stats.averageSeconds * 1000L), Modifier.weight(1f))
            }
            stats.favourite?.let {
                Text("Most made: $it", style = MaterialTheme.typography.titleMedium)
            }
            Text("Recent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            records.takeLast(8).asReversed().forEach { RecentRow(it) }
        }
    }
}

@Composable
private fun StreakCard(stats: Stats) {
    val ink = MaterialTheme.colorScheme.onBackground
    Surface(
        shape = TileShape,
        color = ink,
        contentColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stats.perfectStreak.toString(),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                if (stats.perfectStreak == 1) "tosti in a row, not overcooked" else "tostis in a row, not overcooked",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                when (val days = stats.daysSinceOvercooked) {
                    null -> "You've never overcooked one. Keep it up."
                    0 -> "Last overcooked: today"
                    1 -> "Last overcooked: yesterday"
                    else -> "Last overcooked: $days days ago"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun RecentRow(r: TostiRecord) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${r.label} tosti · ${formatDuration(r.seconds * 1000L)}", style = MaterialTheme.typography.titleMedium)
            Text(
                DateUtils.getRelativeTimeSpanString(r.finishedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            if (r.overcooked) "Overcooked" else "Perfect",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (r.overcooked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Shared frame for the secondary screens: a back link, a title and scrolling content. */
@Composable
internal fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text(
                        "‹ Back",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }
}
