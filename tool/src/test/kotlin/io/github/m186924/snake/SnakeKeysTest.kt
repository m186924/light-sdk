package io.github.m186924.snake

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnakeKeysTest {

    @Test
    fun `d-pad and number pad map to game keys`() {
        assertEquals(SnakeKey.Arrow(Direction.Up), snakeKeyFor(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(SnakeKey.Arrow(Direction.Right), snakeKeyFor(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(SnakeKey.Ok, snakeKeyFor(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(SnakeKey.Ok, snakeKeyFor(KeyEvent.KEYCODE_ENTER))
        assertEquals(SnakeKey.Digit(7), snakeKeyFor(KeyEvent.KEYCODE_7))
        assertEquals(SnakeKey.Digit(3), snakeKeyFor(KeyEvent.KEYCODE_NUMPAD_3))
    }

    @Test
    fun `volume keys are left for LightOS`() {
        assertNull(snakeKeyFor(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(snakeKeyFor(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun `2 4 6 8 steer and 5 doubles as OK`() {
        assertEquals(Direction.Up, SnakeKey.Digit(2).asDirection())
        assertEquals(Direction.Left, SnakeKey.Digit(4).asDirection())
        assertEquals(Direction.Right, SnakeKey.Digit(6).asDirection())
        assertEquals(Direction.Down, SnakeKey.Digit(8).asDirection())
        assertNull(SnakeKey.Digit(5).asDirection())
        assertTrue(SnakeKey.Digit(5).isOk)
        assertFalse(SnakeKey.Digit(1).isOk)
    }
}
