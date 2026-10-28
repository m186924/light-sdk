package io.github.m186924.snake

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import kotlin.math.abs

@InitialScreen
class SnakeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SnakeViewModel>(sealedActivity) {

    private val vibrator = sealedActivity.vibrator

    override val viewModelClass: Class<SnakeViewModel>
        get() = SnakeViewModel::class.java

    override fun createViewModel() = SnakeViewModel(SnakePreferences(lightContext.dataStore), vibrator)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val hapticsEnabled = LocalHapticsEnabled.current
        LaunchedEffect(hapticsEnabled) { viewModel.hapticsEnabled = hapticsEnabled }

        // The Flip has no touchscreen, so hints there name keys; on an LP3 they talk about taps.
        val touch = LocalConfiguration.current.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (state.phase) {
                    SnakePhase.Menu -> SnakeMenu(state, touch, onSelect = viewModel::select)
                    SnakePhase.GameOver -> GameOver(
                        state = state,
                        touch = touch,
                        onPlayAgain = viewModel::start,
                        onMenu = viewModel::toMenu,
                    )
                    SnakePhase.Playing, SnakePhase.Paused, SnakePhase.Dying -> Playfield(
                        state = state,
                        touch = touch,
                        onSwipe = viewModel::steer,
                        onTap = viewModel::tap,
                    )
                }
            }
        }
    }
}

@Composable
private fun SnakeMenu(state: SnakeUiState, touch: Boolean, onSelect: (MenuItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 1.5f.gridUnitsAsDp(), vertical = 1.5f.verticalGridUnitsAsDp()),
    ) {
        LightText(text = "Snake", variant = LightTextVariant.Subtitle)
        LightText(
            text = "Best ${state.best}",
            variant = LightTextVariant.Fine,
            lighten = true,
            modifier = Modifier.padding(top = 0.5f.verticalGridUnitsAsDp()),
        )
        Spacer(Modifier.weight(1f))
        MenuRow("Play", value = null, focused = state.focus == MenuItem.Play) { onSelect(MenuItem.Play) }
        MenuRow("Speed", value = "${state.speed}", focused = state.focus == MenuItem.Speed) {
            onSelect(MenuItem.Speed)
        }
        MenuRow("Walls", value = if (state.walls) "On" else "Off", focused = state.focus == MenuItem.Walls) {
            onSelect(MenuItem.Walls)
        }
        Spacer(Modifier.weight(1f))
        Hint(if (touch) "Tap to choose" else "OK to choose · 1–9 sets speed")
    }
}

/**
 * Inverted when focused, like a Nokia menu bar. The row is clickable for touchscreens but kept
 * out of Compose focus, so D-pad presses reach the view model instead of moving focus around.
 */
@Composable
private fun MenuRow(
    label: String,
    value: String?,
    focused: Boolean,
    // for key labels ("OK", "Back"): smaller, and grey unless the row is highlighted
    trailingLighten: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val text = if (focused) colors.background else colors.content
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .lightClickable(onClick = onClick)
            .background(if (focused) colors.content else Color.Transparent)
            .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.4f.verticalGridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(text = label, variant = LightTextVariant.Heading, color = text, modifier = Modifier.weight(1f))
        if (value != null) {
            LightText(
                text = value,
                variant = if (trailingLighten) LightTextVariant.Fine else LightTextVariant.Heading,
                color = if (trailingLighten && !focused) colors.contentSecondary else text,
            )
        }
    }
}

@Composable
private fun Playfield(
    state: SnakeUiState,
    touch: Boolean,
    onSwipe: (Direction) -> Unit,
    onTap: () -> Unit,
) {
    val game = state.game ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.5f.verticalGridUnitsAsDp()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.5f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Zero-padded like the original's four-digit counter.
            LightText(
                text = game.score.toString().padStart(4, '0'),
                variant = LightTextVariant.Subheading,
                monospace = true,
                modifier = Modifier.weight(1f),
            )
            when {
                state.phase == SnakePhase.Paused -> LightText(text = "Paused", variant = LightTextVariant.Subheading)
                game.bonus != null -> BonusCountdown(game.bonus.ticksLeft)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .swipeToSteer(onSwipe, onTap),
            contentAlignment = Alignment.Center,
        ) {
            SnakeBoard(game = game, snakeVisible = state.snakeVisible)
        }
        if (state.phase == SnakePhase.Paused) {
            Hint(if (touch) "Tap to resume · back for menu" else "OK to resume · back for menu")
        }
    }
}

@Composable
private fun BonusCountdown(ticksLeft: Int) {
    val ink = LightThemeTokens.colors.content
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(20.dp)) { drawBonus(center, size.minDimension, ink) }
        LightText(text = ticksLeft.toString().padStart(2, '0'), variant = LightTextVariant.Subheading, monospace = true)
    }
}

@Composable
private fun GameOver(state: SnakeUiState, touch: Boolean, onPlayAgain: () -> Unit, onMenu: () -> Unit) {
    val game = state.game ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 1.5f.gridUnitsAsDp(), vertical = 1.5f.verticalGridUnitsAsDp()),
    ) {
        LightText(text = if (game.cleared) "Board cleared" else "Game over", variant = LightTextVariant.Subtitle)
        Spacer(Modifier.weight(1f))
        LightText(text = "${game.score}", variant = LightTextVariant.Title)
        LightText(
            text = if (state.newBest) "New best!" else "Best ${state.best}",
            variant = LightTextVariant.Fine,
            lighten = !state.newBest,
        )
        Spacer(Modifier.weight(1f))
        // Without a touchscreen these aren't tappable, so each row names the key that does it.
        MenuRow("Play again", value = if (touch) null else "OK", focused = true, trailingLighten = true, onClick = onPlayAgain)
        MenuRow("Menu", value = if (touch) null else "Back", focused = false, trailingLighten = true, onClick = onMenu)
    }
}

@Composable
private fun Hint(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Detail,
        lighten = true,
        align = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.5f.verticalGridUnitsAsDp()),
    )
}

/**
 * LP3 controls: swipe to steer, tap to pause. A turn fires as soon as the finger has moved far
 * enough, and a new one can fire mid-swipe, so an L-shaped swipe makes two quick turns.
 */
private fun Modifier.swipeToSteer(onSwipe: (Direction) -> Unit, onTap: () -> Unit): Modifier =
    pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }
        .pointerInput(onSwipe) {
            val threshold = 24.dp.toPx()
            var travel = Offset.Zero
            detectDragGestures(onDragStart = { travel = Offset.Zero }) { change, dragAmount ->
                change.consume()
                travel += dragAmount
                if (travel.getDistance() >= threshold) {
                    onSwipe(travel.toDirection())
                    travel = Offset.Zero
                }
            }
        }

private fun Offset.toDirection(): Direction = when {
    abs(x) > abs(y) -> if (x > 0) Direction.Right else Direction.Left
    else -> if (y > 0) Direction.Down else Direction.Up
}
