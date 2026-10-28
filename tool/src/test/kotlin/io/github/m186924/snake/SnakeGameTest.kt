package io.github.m186924.snake

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnakeGameTest {

    private val rules = SnakeRules(width = 10, height = 8, speed = 3)

    // heading right along row 4
    private val snake = listOf(Cell(5, 4), Cell(4, 4), Cell(3, 4))

    private fun game(
        body: List<Cell>,
        direction: Direction = Direction.Right,
        food: Cell? = Cell(0, 0),
        rules: SnakeRules = this.rules,
        bonus: Bonus? = null,
        foodEaten: Int = 0,
    ) = SnakeGame(
        rules = rules,
        body = body,
        direction = direction,
        food = food,
        bonus = bonus,
        foodEaten = foodEaten,
    )

    @Test
    fun `new game starts mid-board heading right with food off the snake`() {
        val game = newGame(SnakeRules(), Random(1))
        assertEquals(START_LENGTH, game.body.size)
        assertEquals(Direction.Right, game.direction)
        assertEquals(Cell(8, 9), game.head)
        val food = assertNotNull(game.food)
        assertFalse(food in game.body)
    }

    @Test
    fun `step moves the head forward and keeps the length`() {
        val next = game(snake).step(Random(0))
        assertEquals(listOf(Cell(6, 4), Cell(5, 4), Cell(4, 4)), next.body)
        assertTrue(next.alive)
    }

    @Test
    fun `eating food grows the snake, scores the speed, and moves the food`() {
        val next = game(snake, food = Cell(6, 4)).step(Random(0))
        assertEquals(4, next.body.size)
        assertEquals(rules.speed, next.score)
        assertEquals(1, next.foodEaten)
        val food = assertNotNull(next.food)
        assertFalse(food in next.body)
    }

    @Test
    fun `reversing into the neck is ignored`() {
        val game = game(snake).turn(Direction.Left)
        assertEquals(emptyList(), game.pendingTurns)
        assertEquals(Cell(6, 4), game.step(Random(0)).head)
    }

    @Test
    fun `two quick turns land on consecutive ticks`() {
        val game = game(snake).turn(Direction.Up).turn(Direction.Left)
        val first = game.step(Random(0))
        assertEquals(Cell(5, 3), first.head)
        assertEquals(Cell(4, 3), first.step(Random(0)).head)
    }

    @Test
    fun `turn queue is capped`() {
        val game = game(snake).turn(Direction.Up).turn(Direction.Left).turn(Direction.Down)
        assertEquals(listOf(Direction.Up, Direction.Left), game.pendingTurns)
    }

    @Test
    fun `open edges wrap around`() {
        val right = game(listOf(Cell(9, 2), Cell(8, 2), Cell(7, 2)))
        assertEquals(Cell(0, 2), right.step(Random(0)).head)
        val up = game(listOf(Cell(4, 0), Cell(4, 1), Cell(4, 2)), direction = Direction.Up)
        assertEquals(Cell(4, 7), up.step(Random(0)).head)
    }

    @Test
    fun `walls kill and leave the body where it crashed`() {
        val game = game(listOf(Cell(9, 2), Cell(8, 2), Cell(7, 2)), rules = rules.copy(walls = true))
        val next = game.step(Random(0))
        assertFalse(next.alive)
        assertEquals(game.body, next.body)
    }

    @Test
    fun `running into yourself kills`() {
        val coiled = listOf(Cell(2, 1), Cell(3, 1), Cell(3, 2), Cell(2, 2), Cell(1, 2))
        val next = game(coiled, direction = Direction.Left).turn(Direction.Down).step(Random(0))
        assertFalse(next.alive)
    }

    @Test
    fun `chasing your own tail is safe`() {
        val loop = listOf(Cell(1, 1), Cell(2, 1), Cell(2, 2), Cell(1, 2))
        val next = game(loop, direction = Direction.Left).turn(Direction.Down).step(Random(0))
        assertTrue(next.alive)
        assertEquals(Cell(1, 2), next.head)
    }

    @Test
    fun `every fifth bite brings a bonus`() {
        val fed = game(snake, food = Cell(6, 4), foodEaten = BONUS_EVERY - 1).step(Random(0))
        val bonus = assertNotNull(fed.bonus)
        assertEquals(BONUS_TICKS, bonus.ticksLeft)
        assertFalse(bonus.cell in fed.body)
        assertNotEquals(fed.food, bonus.cell)
    }

    @Test
    fun `bonus counts down and disappears`() {
        val game = game(snake, food = Cell(0, 7), bonus = Bonus(Cell(0, 0), 2))
        val first = game.step(Random(0))
        assertEquals(1, first.bonus?.ticksLeft)
        assertNull(first.step(Random(0)).bonus)
    }

    @Test
    fun `eating a bonus scores its remaining ticks without growing`() {
        val next = game(snake, food = Cell(0, 7), bonus = Bonus(Cell(6, 4), 17)).step(Random(0))
        assertEquals(17, next.score)
        assertNull(next.bonus)
        assertEquals(snake.size, next.body.size)
    }

    @Test
    fun `filling the board ends the game as cleared`() {
        val tiny = SnakeRules(width = 2, height = 2)
        val game = SnakeGame(
            rules = tiny,
            body = listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1)),
            direction = Direction.Right,
            food = Cell(1, 0),
        )
        val next = game.step(Random(0))
        assertTrue(next.cleared)
        assertFalse(next.alive)
        assertEquals(4, next.body.size)
    }

    @Test
    fun `higher speed levels tick faster`() {
        val ticks = (MIN_SPEED..MAX_SPEED).map(::tickMillis)
        assertEquals(ticks.sortedDescending(), ticks)
        assertEquals(ticks.size, ticks.toSet().size)
        assertEquals(tickMillis(MAX_SPEED), tickMillis(99))
    }
}
