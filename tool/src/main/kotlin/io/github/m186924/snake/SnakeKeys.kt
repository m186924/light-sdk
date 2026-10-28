package io.github.m186924.snake

import android.view.KeyEvent

/** A hardware key, before we know whether it lands on the menu or mid-game. */
sealed interface SnakeKey {
    data class Arrow(val direction: Direction) : SnakeKey
    data class Digit(val value: Int) : SnakeKey
    data object Ok : SnakeKey
}

/**
 * Maps the Light Flip's D-pad and number pad onto game keys. A desktop keyboard's arrows and
 * Enter come through the same way, which makes the emulator playable.
 */
fun snakeKeyFor(keyCode: Int): SnakeKey? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP -> SnakeKey.Arrow(Direction.Up)
    KeyEvent.KEYCODE_DPAD_DOWN -> SnakeKey.Arrow(Direction.Down)
    KeyEvent.KEYCODE_DPAD_LEFT -> SnakeKey.Arrow(Direction.Left)
    KeyEvent.KEYCODE_DPAD_RIGHT -> SnakeKey.Arrow(Direction.Right)
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_SPACE -> SnakeKey.Ok
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> SnakeKey.Digit(keyCode - KeyEvent.KEYCODE_0)
    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> SnakeKey.Digit(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
    else -> null
}

/** Nokia steering: 2 up, 4 left, 6 right, 8 down. */
fun SnakeKey.Digit.asDirection(): Direction? = when (value) {
    2 -> Direction.Up
    4 -> Direction.Left
    6 -> Direction.Right
    8 -> Direction.Down
    else -> null
}

/** 5 sits in the middle of the steering keys, so it doubles as OK, like it did on Nokias. */
val SnakeKey.isOk: Boolean
    get() = this == SnakeKey.Ok || this == SnakeKey.Digit(5)
