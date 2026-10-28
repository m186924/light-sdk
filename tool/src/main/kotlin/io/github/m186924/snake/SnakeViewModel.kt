package io.github.m186924.snake

import android.view.KeyEvent
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightVibrator
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

enum class SnakePhase { Menu, Playing, Paused, Dying, GameOver }

enum class MenuItem { Play, Speed, Walls }

data class SnakeUiState(
    val phase: SnakePhase = SnakePhase.Menu,
    val game: SnakeGame? = null,
    val speed: Int = DEFAULT_SPEED,
    val walls: Boolean = false,
    val focus: MenuItem = MenuItem.Play,
    val best: Int = 0,
    val newBest: Boolean = false,
    // toggled while Dying so the crashed snake blinks, like the original
    val snakeVisible: Boolean = true,
)

private const val DEATH_BLINKS = 3
private const val BLINK_MILLIS = 180L
private val DEATH_BUZZ = 250.milliseconds

class SnakeViewModel(
    private val preferences: SnakePreferences,
    private val vibrator: LightVibrator,
    private val random: Random = Random.Default,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(SnakeUiState())
    val state: StateFlow<SnakeUiState> = _state.asStateFlow()

    /** Mirrors the user's LightOS haptics preference; the screen keeps it current. */
    var hapticsEnabled = false

    // Drives either the game ticks or the death blink, never both.
    private var loop: Job? = null

    init {
        viewModelScope.launch {
            val saved = preferences.load()
            _state.update { it.copy(best = saved.best, speed = saved.speed, walls = saved.walls) }
        }
    }

    // Closing the flip or leaving the screen pauses instead of letting the snake crash.
    override fun onScreenHide(screen: SimpleLightScreen<Unit>) = pause()

    override fun onAppPause() = pause()

    override fun onBackPressed(): Boolean = when (_state.value.phase) {
        SnakePhase.Menu -> false
        SnakePhase.Playing -> {
            pause()
            true
        }
        SnakePhase.Paused, SnakePhase.Dying, SnakePhase.GameOver -> {
            toMenu()
            true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = snakeKeyFor(keyCode) ?: return false
        // Holding a key shouldn't flicker the pause screen or spin through menu values.
        if (event.repeatCount == 0) press(key)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = snakeKeyFor(keyCode) != null

    fun press(key: SnakeKey) {
        when (_state.value.phase) {
            SnakePhase.Menu -> onMenuKey(key)
            SnakePhase.Playing, SnakePhase.Paused -> onGameKey(key)
            SnakePhase.GameOver -> if (key.isOk) start()
            SnakePhase.Dying -> Unit
        }
    }

    /** Swipes and key presses both land here. Steering a paused game resumes it. */
    fun steer(direction: Direction) {
        when (_state.value.phase) {
            SnakePhase.Playing -> _state.update { it.copy(game = it.game?.turn(direction)) }
            SnakePhase.Paused -> {
                _state.update { it.copy(game = it.game?.turn(direction)) }
                resume()
            }
            else -> Unit
        }
    }

    /** A tap on the board, for touchscreen phones: pause, resume, or play again. */
    fun tap() = press(SnakeKey.Ok)

    fun select(item: MenuItem) {
        _state.update { it.copy(focus = item) }
        when (item) {
            MenuItem.Play -> start()
            MenuItem.Speed, MenuItem.Walls -> adjust(+1)
        }
    }

    fun start() {
        val current = _state.value
        _state.value = current.copy(
            phase = SnakePhase.Playing,
            game = newGame(SnakeRules(speed = current.speed, walls = current.walls), random),
            newBest = false,
            snakeVisible = true,
        )
        runLoop()
    }

    fun toMenu() {
        loop?.cancel()
        _state.update {
            it.copy(phase = SnakePhase.Menu, game = null, focus = MenuItem.Play, snakeVisible = true)
        }
    }

    private fun onMenuKey(key: SnakeKey) {
        when (key) {
            SnakeKey.Ok -> select(_state.value.focus)
            is SnakeKey.Digit -> if (key.value in MIN_SPEED..MAX_SPEED) setSpeed(key.value)
            is SnakeKey.Arrow -> when (key.direction) {
                Direction.Up -> moveFocus(-1)
                Direction.Down -> moveFocus(+1)
                Direction.Left -> adjust(-1)
                Direction.Right -> adjust(+1)
            }
        }
    }

    private fun onGameKey(key: SnakeKey) {
        val direction = when (key) {
            is SnakeKey.Arrow -> key.direction
            is SnakeKey.Digit -> key.asDirection()
            SnakeKey.Ok -> null
        }
        when {
            direction != null -> steer(direction)
            key.isOk -> if (_state.value.phase == SnakePhase.Playing) pause() else resume()
        }
    }

    private fun moveFocus(delta: Int) {
        val items = MenuItem.entries
        _state.update { it.copy(focus = items[(it.focus.ordinal + delta).mod(items.size)]) }
    }

    private fun adjust(delta: Int) {
        val current = _state.value
        when (current.focus) {
            MenuItem.Play -> Unit
            MenuItem.Speed -> setSpeed((current.speed - MIN_SPEED + delta).mod(MAX_SPEED) + MIN_SPEED)
            MenuItem.Walls -> setWalls(!current.walls)
        }
    }

    private fun setSpeed(speed: Int) {
        _state.update { it.copy(speed = speed, focus = MenuItem.Speed) }
        saveSettings()
    }

    private fun setWalls(walls: Boolean) {
        _state.update { it.copy(walls = walls) }
        saveSettings()
    }

    private fun saveSettings() {
        val current = _state.value
        viewModelScope.launch { preferences.saveSettings(current.speed, current.walls) }
    }

    private fun pause() {
        if (_state.value.phase != SnakePhase.Playing) return
        loop?.cancel()
        _state.update { it.copy(phase = SnakePhase.Paused) }
    }

    private fun resume() {
        if (_state.value.phase != SnakePhase.Paused) return
        _state.update { it.copy(phase = SnakePhase.Playing) }
        runLoop()
    }

    private fun runLoop() {
        loop?.cancel()
        loop = viewModelScope.launch {
            while (isActive) {
                delay(tickMillis(_state.value.game?.rules?.speed ?: DEFAULT_SPEED))
                tick()
            }
        }
    }

    private fun tick() {
        val current = _state.value
        val game = current.game ?: return
        if (current.phase != SnakePhase.Playing) return
        val next = game.step(random)
        if (!next.alive) {
            die(next)
            return
        }
        if (next.foodEaten > game.foodEaten) buzz { click() }
        _state.value = current.copy(game = next)
    }

    private fun die(game: SnakeGame) {
        loop?.cancel()
        val current = _state.value
        val newBest = game.score > current.best
        _state.value = current.copy(
            phase = SnakePhase.Dying,
            game = game,
            best = maxOf(current.best, game.score),
            newBest = newBest,
        )
        if (newBest) viewModelScope.launch { preferences.saveBest(game.score) }
        if (!game.cleared) buzz { vibrate(DEATH_BUZZ) }
        loop = viewModelScope.launch {
            repeat(DEATH_BLINKS * 2) {
                delay(BLINK_MILLIS)
                _state.update { it.copy(snakeVisible = !it.snakeVisible) }
            }
            _state.update { it.copy(phase = SnakePhase.GameOver, snakeVisible = true) }
        }
    }

    private inline fun buzz(effect: LightVibrator.() -> Unit) {
        if (hapticsEnabled) vibrator.effect()
    }
}
