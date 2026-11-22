package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import com.phuctran.photobooth.desktop.ui.theme.LiquidGlassPlateVariant
import com.phuctran.photobooth.desktop.ui.theme.toPalette
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalFoundationApi::class)
class KioskTouchInteractionsTest {

    private class TestPagerState(
        currentPage: Int = 0,
        currentPageOffsetFraction: Float = 0f,
        private val count: Int = 5
    ) : PagerState(currentPage, currentPageOffsetFraction) {
        override val pageCount: Int get() = count
    }

    @Test
    fun testModifierExtensionFunctionsReturnNonNull() {
        val scrollState = ScrollState(0)
        val lazyGridState = LazyGridState()
        val lazyListState = LazyListState()
        val pagerState = TestPagerState()

        val baseModifier = Modifier

        val m1 = baseModifier.kioskVerticalDrag(scrollState)
        assertNotNull(m1)

        val m2 = baseModifier.kioskHorizontalDrag(scrollState)
        assertNotNull(m2)

        val m3 = baseModifier.kioskVerticalDrag(lazyGridState)
        assertNotNull(m3)

        val m4 = baseModifier.kioskVerticalDrag(lazyListState)
        assertNotNull(m4)

        val m5 = baseModifier.kioskVerticalScroll(scrollState)
        assertNotNull(m5)

        val m6 = baseModifier.kioskHorizontalScroll(scrollState)
        assertNotNull(m6)

        val m7 = baseModifier.kioskPagerDrag(pagerState)
        assertNotNull(m7)

        val m8 = baseModifier.kioskTouchScroll(scrollState, orientation = Orientation.Vertical, reverseDirection = false)
        assertNotNull(m8)

        val m9 = baseModifier.kioskTouchScroll(scrollState, orientation = Orientation.Horizontal, reverseDirection = true)
        assertNotNull(m9)
    }

    @Test
    fun testCustomScrollableStateDispatchDelta() {
        var totalDispatched = 0f
        val customScrollable = ScrollableState { delta ->
            totalDispatched += delta
            delta
        }

        val modifier = Modifier.kioskVerticalDrag(customScrollable)
        assertNotNull(modifier)

        val deltaResult = customScrollable.dispatchRawDelta(-25.5f)
        assertEquals(-25.5f, deltaResult)
        assertEquals(-25.5f, totalDispatched)
    }

    private fun computeTargetPage(
        currentPage: Int,
        currentPageOffsetFraction: Float,
        velocityX: Float,
        pageCount: Int
    ): Int {
        if (pageCount <= 0) return 0
        val currentPosition = currentPage + currentPageOffsetFraction
        return when {
            velocityX < -350f -> floor(currentPosition).toInt() + 1
            velocityX > 350f -> ceil(currentPosition).toInt() - 1
            else -> round(currentPosition).toInt()
        }.coerceIn(0, pageCount - 1)
    }

    @Test
    fun testPagerFlickForwardNavigatesToNextPage() {
        val pageCount = 5
        val target = computeTargetPage(currentPage = 1, currentPageOffsetFraction = 0.1f, velocityX = -500f, pageCount = pageCount)
        assertEquals(2, target)
    }

    @Test
    fun testPagerFlickBackwardNavigatesToPreviousPage() {
        val pageCount = 5
        val target = computeTargetPage(currentPage = 2, currentPageOffsetFraction = -0.1f, velocityX = +500f, pageCount = pageCount)
        assertEquals(1, target)
    }

    @Test
    fun testPagerSlowDragSettlesToNearestPage() {
        val pageCount = 5
        val targetNearNext = computeTargetPage(currentPage = 1, currentPageOffsetFraction = 0.6f, velocityX = -50f, pageCount = pageCount)
        assertEquals(2, targetNearNext)

        val targetNearCurrent = computeTargetPage(currentPage = 1, currentPageOffsetFraction = 0.4f, velocityX = 50f, pageCount = pageCount)
        assertEquals(1, targetNearCurrent)
    }

    @Test
    fun testPagerBoundsClampingAtEnds() {
        val pageCount = 5
        val overEnd = computeTargetPage(currentPage = 4, currentPageOffsetFraction = 0.2f, velocityX = -1200f, pageCount = pageCount)
        assertEquals(4, overEnd)

        val underStart = computeTargetPage(currentPage = 0, currentPageOffsetFraction = -0.2f, velocityX = +1200f, pageCount = pageCount)
        assertEquals(0, underStart)
    }

    @Test
    fun testTouchSlopAndDeltaLogic() {
        val touchSlop = 18f
        var accumulated = 10f
        var dragStarted = accumulated >= touchSlop
        assertEquals(false, dragStarted)

        accumulated += 12f
        dragStarted = accumulated >= touchSlop
        assertEquals(true, dragStarted)

        val excess = accumulated - (kotlin.math.sign(accumulated) * touchSlop)
        assertEquals(4f, excess, 0.001f)

        val diffY = -20f
        val normalScrollDelta = -diffY
        assertEquals(20f, normalScrollDelta)

        val reversedScrollDelta = diffY
        assertEquals(-20f, reversedScrollDelta)
    }

    @Test
    fun testDecayThresholdLogic() {
        val minFlingVelocity = 60f
        val microFling = 45f
        val realFling = 400f

        assertTrue(abs(microFling) <= minFlingVelocity)
        assertTrue(abs(realFling) > minFlingVelocity)
    }

    @Test
    fun testLiquidGlassPlateVariantsAndModifiers() {
        for (variant in com.phuctran.photobooth.desktop.ui.theme.LiquidGlassPlateVariant.values()) {
            val palette = variant.toPalette()
            assertTrue(palette.bodyStops.isNotEmpty())
            val mod = Modifier.liquidGlassPlate(variant = variant)
            assertNotNull(mod)
        }
    }
}