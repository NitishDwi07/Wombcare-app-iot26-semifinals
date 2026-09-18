package com.silicovegas.wombcare.feature.patient

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silicovegas.wombcare.R
import com.silicovegas.wombcare.core.ui.components.PrimaryButton
import com.silicovegas.wombcare.core.ui.components.SecondaryButton
import com.silicovegas.wombcare.core.ui.theme.LocalStatusColors
import com.silicovegas.wombcare.core.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val PAGES = 11

/**
 * The patient's "How to use WombCare" walkthrough — a tap-through (or swipe) guide the mother
 * can page through at her own pace. Reuses [PlacementDiagram] for the placement steps so the
 * anatomy shown is exactly the flashed contract. Ends on a Start button that returns to the
 * dashboard.
 */
@Composable
fun HowToUseScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1

    // Surface sets the content color to onBackground, so default-colored text stays visible
    // in dark mode (a plain Column leaves it defaulting to black).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize().systemBarsPadding()) {
        // top bar: title + Skip
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.sm, top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "How to use WombCare",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text(if (last) "Close" else "Skip") }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> Welcome()
                    1 -> ChestPage()
                    2 -> BellyPage()
                    3 -> RulePage()
                    4 -> PrepPage()
                    5 -> ConnectPage()
                    6 -> DashboardPage()
                    7 -> StatusMeaningPage()
                    8 -> SharePage()
                    9 -> EverydayPage()
                    else -> DonePage()
                }
            }
        }

        // dots
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(PAGES) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (on) 9.dp else 7.dp)
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
            }
        }

        // nav buttons
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (pager.currentPage > 0) {
                SecondaryButton(
                    "Back",
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                if (last) "Start monitoring" else "Next",
                onClick = {
                    if (last) onDone()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.weight(1f),
            )
        }
      }
    }
}

/* ---- pages ---- */

@Composable
private fun Eyebrow(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.SemiBold,
)

@Composable
private fun Title(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center,
)

@Composable
private fun Body(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

@Composable
private fun Welcome() {
    Icon(
        Icons.Rounded.MonitorHeart, contentDescription = null,
        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp),
    )
    Spacer(Modifier.height(Spacing.lg))
    Eyebrow("WombCare · Fetal wellness")
    Spacer(Modifier.height(Spacing.sm))
    Title("Let's set up your device")
    Spacer(Modifier.height(Spacing.sm))
    Body("A quick guide to wearing WombCare correctly — about a minute.")
}

@Composable
private fun DiagramHeader(step: String, title: String) {
    Eyebrow(step)
    Spacer(Modifier.height(Spacing.xs))
    Title(title)
    Spacer(Modifier.height(Spacing.md))
}

@Composable
private fun Diagram(@DrawableRes image: Int) {
    // A real annotated photo on a clean panel that matches its background.
    Surface(
        color = Color(0xFFF4EFEA),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(image),
                contentDescription = "Electrode placement shown on an expectant mother",
                modifier = Modifier.heightIn(max = 440.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        "Shown as if you're facing the mother — her right is on your left.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Lead(color: Color, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).background(color, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChestPage() {
    val s = LocalStatusColors.current
    DiagramHeader("1 · Placement — chest", "Three pads on the chest")
    Diagram(R.drawable.howto_chest)
    Spacer(Modifier.height(Spacing.lg))
    Lead(Color(0xFFDE3C41), "RED — hollow below the mother's RIGHT collarbone")
    Lead(Color(0xFFCF8A12), "YELLOW — hollow below the mother's LEFT collarbone")
    Lead(Color(0xFF1F9B74), "GREEN — lower-LEFT ribs (mother's left side)")
    Spacer(Modifier.height(Spacing.sm))
    Text(
        "These three sit on the chest and give a clean reading of the mother's own heartbeat.",
        style = MaterialTheme.typography.bodyMedium, color = s.suspect, textAlign = TextAlign.Center,
    )
}

@Composable
private fun BellyPage() {
    DiagramHeader("2 · Placement — belly", "Three pads on the belly")
    Diagram(R.drawable.howto_belly)
    Spacer(Modifier.height(Spacing.lg))
    Lead(Color(0xFFDE3C41), "RED — on the midline, ABOVE the navel (fundus)")
    Lead(Color(0xFFCF8A12), "YELLOW — on the midline, BELOW the navel (suprapubic)")
    Lead(Color(0xFF1F9B74), "GREEN — the mother's RIGHT hip bone (iliac crest)")
    Spacer(Modifier.height(Spacing.sm))
    Body("These three sit on the belly, where the baby's heartbeat is picked up.")
}

@Composable
private fun RulePage() {
    DiagramHeader("3 · The one rule", "Keep the belly pads 10–15 cm apart")
    Diagram(R.drawable.howto_rule)
    Spacer(Modifier.height(Spacing.lg))
    Body("Place the two belly pads a hand-width apart. Too close, and the fetal signal cancels out.")
}

@Composable
private fun PrepPage() {
    Box(
        Modifier.size(120.dp).background(LocalStatusColors.current.normalContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check, contentDescription = null,
            tint = LocalStatusColors.current.normal, modifier = Modifier.size(56.dp),
        )
    }
    Spacer(Modifier.height(Spacing.lg))
    Eyebrow("4 · Prep the skin")
    Spacer(Modifier.height(Spacing.sm))
    Title("Clean, dry, hair-free")
    Spacer(Modifier.height(Spacing.sm))
    Body("Press each pad flat against the skin, with no air gaps.")
}

@Composable
private fun ConnectPage() {
    Icon(
        Icons.Rounded.Bluetooth, contentDescription = null,
        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp),
    )
    Spacer(Modifier.height(Spacing.md))
    Eyebrow("5 · Connect")
    Spacer(Modifier.height(Spacing.md))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Step(1, "Open the WombCare app")
        Step(2, "Tap Start monitoring")
        Step(3, "Choose your device from the list")
        Step(4, "Enter PIN 123456")
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        "You'll only enter the PIN once.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DashboardPage() {
    Icon(
        Icons.Rounded.MonitorHeart, contentDescription = null,
        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp),
    )
    Spacer(Modifier.height(Spacing.md))
    Eyebrow("6 · Your dashboard")
    Spacer(Modifier.height(Spacing.xs))
    Title("What you'll see")
    Spacer(Modifier.height(Spacing.md))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        InfoRow("Fetal heart rate", "your baby's heartbeat, in beats per minute")
        InfoRow("Mother's heart rate", "your own pulse")
        InfoRow("Kicks", "movements counted this session")
        InfoRow("AI accuracy", "how confident the reading is")
        InfoRow("Motion & battery", "whether you're resting, and the device's charge")
    }
}

@Composable
private fun StatusMeaningPage() {
    val s = LocalStatusColors.current
    Eyebrow("7 · Understanding your status")
    Spacer(Modifier.height(Spacing.lg))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatusChip("Normal", s.normal)
        StatusChip("Elevated", s.suspect)
        StatusChip("Pathological", s.pathologic)
    }
    Spacer(Modifier.height(Spacing.lg))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        InfoRow("Normal", "all looks well — keep monitoring")
        InfoRow("Elevated", "worth keeping an eye on — not an emergency")
        InfoRow("Pathological", "contact your doctor")
    }
    Spacer(Modifier.height(Spacing.sm))
    Body("Give it about a minute for the first reading to appear.")
}

@Composable
private fun SharePage() {
    Icon(
        Icons.Rounded.Share, contentDescription = null,
        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp),
    )
    Spacer(Modifier.height(Spacing.md))
    Eyebrow("8 · Share with your doctor")
    Spacer(Modifier.height(Spacing.md))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Step(1, "Open Share (top-right on the dashboard)")
        Step(2, "Turn on Sharing")
        Step(3, "Read your code to your doctor")
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        "You're in control — turn sharing off any time.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EverydayPage() {
    Icon(
        Icons.Rounded.Settings, contentDescription = null,
        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp),
    )
    Spacer(Modifier.height(Spacing.md))
    Eyebrow("9 · Everyday")
    Spacer(Modifier.height(Spacing.xs))
    Title("Good to know")
    Spacer(Modifier.height(Spacing.md))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        InfoRow("Pause & resume", "tap Stop to pause, Start to continue")
        InfoRow("New device", "use Forget device in Settings — it'll ask for the PIN again")
        InfoRow("This guide", "reopen it any time from the ? icon or Settings")
    }
}

@Composable
private fun InfoRow(title: String, desc: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 7.dp).size(7.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).background(color, CircleShape))
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DonePage() {
    Box(
        Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check, contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(56.dp),
        )
    }
    Spacer(Modifier.height(Spacing.lg))
    Title("You're all set")
    Spacer(Modifier.height(Spacing.sm))
    Body("Wear the device, then tap Start monitoring on the dashboard.")
}
