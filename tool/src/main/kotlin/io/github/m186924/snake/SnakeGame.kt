package io.github.m186924.snake

import kotlin.random.Random

/** A square on the board; (0, 0) is the top-left corner. */
data class Cell(val x: Int, val y: Int)

enum class Direction(val dx: Int, val dy: Int) {
    Up(0, -1),
    Down(0, 1),
    Left(-1, 0),
    Right(1, 0);

    val opposite: Direction
        get() = when (this) {
            Up -> Down
            Down -> Up
            Left -> Right
            Right -> Left
        }
}

const val MIN_SPEED = 1
const val MAX_SPEED = 9
const val DEFAULT_SPEED = 5
const val START_LENGTH = 4

/** A bonus critter shows up after every this-many bites. */
const val BONUS_EVERY = 5

/** Bonus critters are worth their remaining ticks, so they shrink in value while they wait. */
const val BONUS_TICKS = 30

/** Lets a fast double-tap (up, then left) land as two turns instead of one. */
const val MAX_QUEUED_TURNS = 2

private val TICK_MILLIS = longArrayOf(400, 340, 285, 235, 190, 150, 120, 95, 75)

/** Nokia-style speed levels: 1 is a stroll, 9 is a blur. */
fun tickMillis(speed: Int): Long = TICK_MILLIS[speed.coerceIn(MIN_SPEED, MAX_SPEED) - MIN_SPEED]

data class SnakeRules(
    val width: Int = 16,
    val height: Int = 18,
    val speed: Int = DEFAULT_SPEED,
    // With walls off the snake wraps around the edges, like Snake II's open field.
    val walls: Boolean = false,
)

data class Bonus(val cell: Cell, val ticksLeft: Int)

data class SnakeGame(
    val rules: SnakeRules,
    val body: List<Cell>, // head first
    val direction: Direction,
    // null only once the snake fills the board and there's nowhere left to put it
    val food: Cell?,
    val pendingTurns: List<Direction> = emptyList(),
    val bonus: Bonus? = null,
    val score: Int = 0,
    val foodEaten: Int = 0,
    val alive: Boolean = true,
) {
    val head: Cell get() = body.first()
    val cleared: Boolean get() = food == null
}

fun newGame(rules: SnakeRules, random: Random): SnakeGame {
    val y = rules.height / 2
    val headX = rules.width / 2
    val body = List(START_LENGTH) { Cell(headX - it, y) }
    return SnakeGame(
        rules = rules,
        body = body,
        direction = Direction.Right,
        food = randomFreeCell(rules, body.toSet(), random),
    )
}

/** Queues a turn for an upcoming tick. Reversing straight into your own neck is ignored. */
fun SnakeGame.turn(direction: Direction): SnakeGame {
    if (!alive || pendingTurns.size >= MAX_QUEUED_TURNS) return this
    val last = pendingTurns.lastOrNull() ?: this.direction
    if (direction == last || direction == last.opposite) return this
    return copy(pendingTurns = pendingTurns + direction)
}

/** Advances one tick. A dead snake keeps its last body so the screen can flash it. */
fun SnakeGame.step(random: Random): SnakeGame {
    if (!alive) return this
    val turned = copy(
        direction = pendingTurns.firstOrNull() ?: direction,
        pendingTurns = pendingTurns.drop(1),
    )
    val next = turned.nextHead() ?: return turned.copy(alive = false)

    val eats = next == food
    // The tail moves out of the way this tick unless the snake is growing, so chasing it is safe.
    val kept = if (eats) body else body.dropLast(1)
    if (next in kept) return turned.copy(alive = false)

    val newBody = listOf(next) + kept
    val eatenBonus = bonus?.takeIf { it.cell == next }
    val remainingBonus = if (eatenBonus != null) null else bonus?.tick()
    val newFoodEaten = if (eats) foodEaten + 1 else foodEaten
    val newFood = if (eats) {
        randomFreeCell(rules, newBody.toSet() + listOfNotNull(remainingBonus?.cell), random)
    } else {
        food
    }
    val newBonus = if (eats && newFood != null && newFoodEaten % BONUS_EVERY == 0) {
        randomFreeCell(rules, newBody.toSet() + newFood, random)?.let { Bonus(it, BONUS_TICKS) }
    } else {
        remainingBonus
    }

    return turned.copy(
        body = newBody,
        food = newFood,
        bonus = newBonus,
        score = score + (if (eats) rules.speed else 0) + (eatenBonus?.ticksLeft ?: 0),
        foodEaten = newFoodEaten,
        alive = newFood != null,
    )
}

private fun Bonus.tick(): Bonus? = if (ticksLeft > 1) copy(ticksLeft = ticksLeft - 1) else null

private fun SnakeGame.nextHead(): Cell? {
    val x = head.x + direction.dx
    val y = head.y + direction.dy
    return when {
        x in 0 until rules.width && y in 0 until rules.height -> Cell(x, y)
        rules.walls -> null
        else -> Cell(x.mod(rules.width), y.mod(rules.height))
    }
}

internal fun randomFreeCell(rules: SnakeRules, taken: Set<Cell>, random: Random): Cell? {
    val free = buildList {
        for (y in 0 until rules.height) {
            for (x in 0 until rules.width) {
                val cell = Cell(x, y)
                if (cell !in taken) add(cell)
            }
        }
    }
    return free.randomOrNull(random)
}
