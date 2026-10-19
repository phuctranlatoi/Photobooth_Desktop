package com.phuctran.photobooth.desktop.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Compose Desktop treats many Windows touchscreen swipes as mouse drags. */
fun Modifier.kioskVerticalDrag(state: LazyGridState): Modifier = pointerInput(state) {
    detectDragGestures(
        onDragEnd = { /* Raw drag deltas stop at release; the grid retains its final position. */ },
        onDragCancel = { /* No pending drag work to release. */ },
        onDrag = { change, amount ->
            if (change.type == PointerType.Mouse) {
                state.dispatchRawDelta(-amount.y)
                change.consume()
            }
        }
    )
}

fun Modifier.kioskHorizontalDrag(state: ScrollState): Modifier = pointerInput(state) {
    detectDragGestures(
        onDragEnd = { /* Raw drag deltas stop at release. */ },
        onDragCancel = { /* No pending drag work to release. */ },
        onDrag = { change, amount ->
            if (change.type == PointerType.Mouse) {
                state.dispatchRawDelta(-amount.x)
                change.consume()
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.kioskPagerDrag(state: PagerState): Modifier = pointerInput(state) {
    val scope = CoroutineScope(coroutineContext)
    fun settle() {
        if (state.pageCount == 0) return
        val page = state.currentPage.coerceIn(0, state.pageCount - 1)
        scope.launch { state.animateScrollToPage(page) }
    }
    detectDragGestures(
        onDragEnd = { settle() },
        onDragCancel = { settle() },
        onDrag = { change, amount ->
            if (change.type == PointerType.Mouse) {
                state.dispatchRawDelta(-amount.x)
                change.consume()
            }
        }
    )
}
