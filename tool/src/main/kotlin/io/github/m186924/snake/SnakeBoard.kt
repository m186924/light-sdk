package io.github.m186924.snake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlin.math.abs
import kotlin.math.min

// Everything is drawn in two tones so it reads the same on the Flip's monochrome OLED as on an LP3.

/** Bonus critters blink for their last few ticks, a warning that they're about to leave. */
private const val BONUS_BLINK_TICKS = 6

@Composable
fun SnakeBoard(
    game: SnakeGame,
    snakeVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = LightThemeTokens.colors.content
    val paper = LightThemeTokens.colors.background
    val rules = game.rules
    // Half a cell of margin on every side leaves room for the frame outside the play area.
    Canvas(modifier.aspectRatio((rules.width + 1f) / (rules.height + 1f))) {
        val cell = min(size.width / (rules.width + 1), size.height / (rules.height + 1))
        val origin = Offset(
            (size.width - cell * rules.width) / 2,
            (size.height - cell * rules.height) / 2,
        )
        val grid = BoardGrid(origin, cell)

        drawFrame(grid, rules, ink)
        game.food?.let { drawFood(grid, it, ink) }
        game.bonus?.let { bonus ->
            if (bonus.ticksLeft > BONUS_BLINK_TICKS || bonus.ticksLeft % 2 == 0) {
                drawBonus(grid.center(bonus.cell), cell, ink)
            }
        }
        if (snakeVisible) drawSnake(grid, game, ink, paper)
    }
}

private class BoardGrid(val origin: Offset, val cell: Float) {
    fun topLeft(c: Cell) = Offset(origin.x + c.x * cell, origin.y + c.y * cell)
    fun center(c: Cell) = topLeft(c) + Offset(cell / 2, cell / 2)
}

private fun DrawScope.drawFrame(grid: BoardGrid, rules: SnakeRules, ink: Color) {
    val cell = grid.cell
    val inset = cell / 4
    val topLeft = grid.origin - Offset(inset, inset)
    val frameSize = Size(cell * rules.width + inset * 2, cell * rules.height + inset * 2)
    // A solid frame means the edges kill; a dotted one means the snake wraps through them.
    val stroke = if (rules.walls) {
        Stroke(width = cell * 0.3f)
    } else {
        Stroke(
            width = cell * 0.1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.3f, cell * 0.3f)),
        )
    }
    drawRect(ink, topLeft = topLeft, size = frameSize, style = stroke)
}

private fun DrawScope.drawSnake(grid: BoardGrid, game: SnakeGame, ink: Color, paper: Color) {
    val cell = grid.cell
    val gap = cell * 0.12f
    val block = Size(cell - gap * 2, cell - gap * 2)
    game.body.forEach { drawRect(ink, topLeft = grid.topLeft(it) + Offset(gap, gap), size = block) }

    // Bridge neighbouring segments so the body reads as one line, except where it wraps an edge.
    game.body.zipWithNext { a, b ->
        if (abs(a.x - b.x) + abs(a.y - b.y) != 1) return@zipWithNext
        val first = grid.topLeft(Cell(min(a.x, b.x), min(a.y, b.y)))
        if (a.y == b.y) {
            drawRect(ink, topLeft = first + Offset(cell - gap, gap), size = Size(gap * 2, cell - gap * 2))
        } else {
            drawRect(ink, topLeft = first + Offset(gap, cell - gap), size = Size(cell - gap * 2, gap * 2))
        }
    }

    // One eye, looking where the snake is headed.
    val forward = Offset(game.direction.dx.toFloat(), game.direction.dy.toFloat())
    val side = Offset(forward.y, -forward.x)
    val eye = cell * 0.2f
    val eyeCenter = grid.center(game.head) + forward * (cell * 0.15f) + side * (cell * 0.15f)
    drawRect(paper, topLeft = eyeCenter - Offset(eye / 2, eye / 2), size = Size(eye, eye))
}

/** Four pixels in a diamond, the way food looked on a Nokia. */
private fun DrawScope.drawFood(grid: BoardGrid, food: Cell, ink: Color) {
    val pixel = grid.cell / 4
    val c = grid.center(food)
    listOf(Offset(0f, -1f), Offset(-1f, 0f), Offset(1f, 0f), Offset(0f, 1f)).forEach {
        drawRect(ink, topLeft = c + it * pixel - Offset(pixel / 2, pixel / 2), size = Size(pixel, pixel))
    }
}

/** Five pixels in an X: a little critter that's clearly not food. */
fun DrawScope.drawBonus(center: Offset, cell: Float, ink: Color) {
    val pixel = cell * 0.26f
    listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(0f, 0f), Offset(-1f, 1f), Offset(1f, 1f)).forEach {
        drawRect(ink, topLeft = center + it * pixel - Offset(pixel / 2, pixel / 2), size = Size(pixel, pixel))
    }
}
