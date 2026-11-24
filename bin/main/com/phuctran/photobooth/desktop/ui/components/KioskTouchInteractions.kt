package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sign

/**
 * Universal touch & mouse drag scroll modifier for Kiosk screens.
 *
 * Supports ALL pointer types (Touch, Mouse, Stylus) without filtering out PointerType.Touch.
 * Provides smooth inertia fling decay animation via [exponentialDecay] and cancels active flings on touch down.
 * Enforces touch slop threshold before consuming pointer movement so child clicks/taps remain fully responsive.
 * Works polymorphically with any [ScrollableState] (ScrollState, LazyGridState, LazyListState).
 */
fun Modifier.kioskTouchScroll(
    state: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
    reverseDirection: Boolean = false
): Modifier = pointerInput(state, orientation, reverseDirection) {
    coroutineScope {
        var flingJob: Job? = null
        val decay = exponentialDecay<Float>(
            frictionMultiplier = 1.15f,
            absVelocityThreshold = 0.5f
        )

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Immediately halt any in-flight fling animation when user touches down
            flingJob?.cancel()

            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var dragStarted = false
            var accumulatedDelta = 0f
            val touchSlop = viewConfiguration.touchSlop
            val pointerId = down.id

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (change.isConsumed) break

                if (!change.pressed) {
                    // Pointer released: perform inertia fling if dragging occurred
                    if (dragStarted) {
                        val velocity = velocityTracker.calculateVelocity()
                        val rawVelocity = if (orientation == Orientation.Vertical) velocity.y else velocity.x
                        val flingVelocity = if (reverseDirection) rawVelocity else -rawVelocity

                        if (abs(flingVelocity) > 60f) {
                            flingJob = launch {
                                var lastValue = 0f
                                val animationState = AnimationState(
                                    initialValue = 0f,
                                    initialVelocity = flingVelocity
                                )
                                try {
                                    state.scroll(MutatePriority.Default) {
                                        animationState.animateDecay(decay) {
                                            val stepDelta = value - lastValue
                                            lastValue = value
                                            val consumed = scrollBy(stepDelta)
                                            if (abs(stepDelta - consumed) > 0.5f) {
                                                cancelAnimation()
                                            }
                                        }
                                    }
                                } catch (_: CancellationException) {
                                    // Interrupted by next touch contact
                                }
                            }
                        }
                    }
                    break
                }

                val currentPos = change.position
                val prevPos = change.previousPosition
                val diff = if (orientation == Orientation.Vertical) {
                    currentPos.y - prevPos.y
                } else {
                    currentPos.x - prevPos.x
                }

                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!dragStarted) {
                    accumulatedDelta += diff
                    if (abs(accumulatedDelta) >= touchSlop) {
                        dragStarted = true
                        val excess = accumulatedDelta - (sign(accumulatedDelta) * touchSlop)
                        val scrollDelta = if (reverseDirection) excess else -excess
                        state.dispatchRawDelta(scrollDelta)
                        change.consume()
                    }
                } else {
                    val scrollDelta = if (reverseDirection) diff else -diff
                    state.dispatchRawDelta(scrollDelta)
                    change.consume()
                }
            }
        }
    }
}

/** Vertical drag scroll with inertia for any [ScrollableState] (LazyGridState, LazyListState, ScrollState). */
fun Modifier.kioskVerticalDrag(state: ScrollableState): Modifier =
    kioskTouchScroll(state = state, orientation = Orientation.Vertical, reverseDirection = false)

/** Horizontal drag scroll with inertia for any [ScrollableState] (ScrollState, LazyListState). */
fun Modifier.kioskHorizontalDrag(state: ScrollableState): Modifier =
    kioskTouchScroll(state = state, orientation = Orientation.Horizontal, reverseDirection = false)

/** Combined vertical scroll and touch drag helper for [ScrollState]. */
fun Modifier.kioskVerticalScroll(state: ScrollState): Modifier =
    this.verticalScroll(state).kioskVerticalDrag(state)

/** Combined horizontal scroll and touch drag helper for [ScrollState]. */
fun Modifier.kioskHorizontalScroll(state: ScrollState): Modifier =
    this.horizontalScroll(state).kioskHorizontalDrag(state)

/**
 * Smooth horizontal drag modifier for [PagerState] on Kiosk touchscreen.
 * Eliminates page bounce-back by computing target page from flick velocityX and offset fraction.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.kioskPagerDrag(state: PagerState): Modifier = pointerInput(state) {
    coroutineScope {
        var scrollJob: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            scrollJob?.cancel()

            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var dragStarted = false
            var accumulatedDeltaX = 0f
            val touchSlop = viewConfiguration.touchSlop
            val pointerId = down.id

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (change.isConsumed) break

                if (!change.pressed) {
                    if (dragStarted && state.pageCount > 0) {
                        val velocityX = velocityTracker.calculateVelocity().x
                        val currentPosition = state.currentPage + state.currentPageOffsetFraction
                        val targetPage = when {
                            velocityX < -350f -> floor(currentPosition).toInt() + 1
                            velocityX > 350f -> ceil(currentPosition).toInt() - 1
                            else -> round(currentPosition).toInt()
                        }.coerceIn(0, state.pageCount - 1)

                        scrollJob = launch {
                            try {
                                state.animateScrollToPage(targetPage)
                            } catch (_: CancellationException) {
                                // Interrupted by user touch
                            }
                        }
                    }
                    break
                }

                val currentPos = change.position
                val prevPos = change.previousPosition
                val diffX = currentPos.x - prevPos.x

                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!dragStarted) {
                    accumulatedDeltaX += diffX
                    if (abs(accumulatedDeltaX) >= touchSlop) {
                        dragStarted = true
                        val excess = accumulatedDeltaX - (sign(accumulatedDeltaX) * touchSlop)
                        state.dispatchRawDelta(-excess)
                        change.consume()
                    }
                } else {
                    state.dispatchRawDelta(-diffX)
                    change.consume()
                }
            }
        }
    }
}
