package com.myenvironment.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.myenvironment.launcher.core.model.*
import com.myenvironment.launcher.ui.home.CrossPageDragState
import com.myenvironment.launcher.ui.home.DragOrigin
import kotlin.math.abs

class LauncherDragController {
    internal val sources = linkedMapOf<String, LauncherDragSource>()
}

internal class LauncherDragSource {
    var bounds = Rect.Zero
    var origin = DragOrigin.HOME
    var enabled = true
    var afterLongPress = true
    var createState: (Offset) -> CrossPageDragState? = { null }
    var onLongPressRelease: () -> Unit = {}
}

val LocalLauncherDragController = staticCompositionLocalOf<LauncherDragController?> { null }

/** Sources only register their geometry. The persistent root owns the entire pointer sequence. */
@Composable
fun Modifier.launcherDragSource(
    key: String,
    origin: DragOrigin,
    enabled: Boolean = true,
    afterLongPress: Boolean = true,
    onLongPressRelease: () -> Unit = {},
    createState: (Offset) -> CrossPageDragState?
): Modifier {
    val controller = LocalLauncherDragController.current ?: return this
    val source = remember(controller, key) { LauncherDragSource() }
    val registryKey = remember(controller, key) { "$key:${java.util.UUID.randomUUID()}" }
    SideEffect {
        source.origin = origin
        source.enabled = enabled
        source.afterLongPress = afterLongPress
        source.createState = createState
        source.onLongPressRelease = onLongPressRelease
    }
    DisposableEffect(controller, registryKey) {
        controller.sources[registryKey] = source
        onDispose { if (controller.sources[registryKey] === source) controller.sources.remove(registryKey) }
    }
    return onGloballyPositioned { source.bounds = it.boundsInRoot() }
}

/** A secondary pointer never becomes the drag pointer, including when the primary is released. */
internal class SecondaryPageSwipe(private val primaryId: Long, private val threshold: Float) {
    private var secondaryId: Long? = null
    private var start = Offset.Zero
    fun down(id: Long, position: Offset, insidePager: Boolean) {
        if (id != primaryId && secondaryId == null && insidePager) {
            secondaryId = id
            start = position
        }
    }
    fun owns(id: Long) = secondaryId == id
    fun up(id: Long, position: Offset): Int {
        if (!owns(id)) return 0
        secondaryId = null
        val delta = position - start
        return when {
            abs(delta.x) < threshold || abs(delta.x) < abs(delta.y) * 1.5f -> 0
            delta.x < 0 -> 1
            else -> -1
        }
    }
}

@Composable
fun Modifier.launcherDragHost(
    controller: LauncherDragController,
    activeDrag: () -> CrossPageDragState?,
    canStart: (DragOrigin) -> Boolean,
    pagerBounds: () -> Rect,
    onStart: (CrossPageDragState) -> Boolean,
    onUpdate: (CrossPageDragState) -> Unit,
    onEnd: (CrossPageDragState) -> Unit,
    onCancel: () -> Unit,
    onSecondaryActive: (Boolean) -> Unit,
    onPageSwipe: (Int) -> Unit
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentDrag by rememberUpdatedState(activeDrag)
    val eligible by rememberUpdatedState(canStart)
    val bounds by rememberUpdatedState(pagerBounds)
    val start by rememberUpdatedState(onStart)
    val update by rememberUpdatedState(onUpdate)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    val secondaryActive by rememberUpdatedState(onSecondaryActive)
    val pageSwipe by rememberUpdatedState(onPageSwipe)
    return onGloballyPositioned { coordinates = it }.pointerInput(controller) {
        val pagingThreshold = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val rootCoordinates = coordinates ?: return@awaitEachGesture
            val downRoot = rootCoordinates.localToRoot(down.position)
            val source = controller.sources.values.lastOrNull {
                it.enabled && eligible(it.origin) && it.bounds.contains(downRoot)
            } ?: return@awaitEachGesture
            var longPressed = false
            var dragging = false
            var latest: CrossPageDragState? = null
            var grabOffset = Offset.Zero
            val second = SecondaryPageSwipe(down.id.value, pagingThreshold)
            val longPressDeadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
            var lastTime = down.uptimeMillis
            try {
                while (true) {
                    val event = if (!longPressed && !dragging) {
                        withTimeoutOrNull((longPressDeadline - lastTime).coerceAtLeast(1)) {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                    } else awaitPointerEvent(PointerEventPass.Initial)
                    if (event == null) { longPressed = true; continue }
                    val primary = event.changes.firstOrNull { it.id == down.id } ?: break
                    lastTime = primary.uptimeMillis
                    if (lastTime >= longPressDeadline) longPressed = true
                    val coords = coordinates ?: break
                    if (!coords.isAttached) break
                    val finger = coords.localToRoot(primary.position)
                    if (!dragging) {
                        if (!primary.pressed) {
                            if (longPressed && controller.sources.values.any { it === source }) {
                                primary.consume()
                                source.onLongPressRelease()
                            }
                            break
                        }
                        if (event.changes.any { it.id != down.id && it.pressed }) break
                        val moved = (finger - downRoot).getDistance() > viewConfiguration.touchSlop
                        if (moved) {
                            if (source.afterLongPress && !longPressed) break
                            val state = source.createState(finger) ?: break
                            if (!start(state)) { primary.consume(); break }
                            grabOffset = state.fingerInRoot - state.topLeftInRoot
                            latest = state
                            dragging = true
                            primary.consume()
                        }
                    } else {
                        if (currentDrag() == null) { primary.consume(); break }
                        val state = latest!!.copy(fingerInRoot = finger, topLeftInRoot = finger - grabOffset)
                        latest = state
                        primary.consume()
                        // Release the original finger first: commit once, without pointer handoff.
                        if (!primary.pressed) { end(state); dragging = false; break }
                        update(state)
                        for (change in event.changes) {
                            if (change.id == down.id) continue
                            val position = coords.localToRoot(change.position)
                            // Suppress incidental taps from additional fingers during a layout drag.
                            change.consume()
                            if (change.pressed && !change.previousPressed) {
                                second.down(change.id.value, position, bounds().contains(position))
                            }
                            if (second.owns(change.id.value)) {
                                secondaryActive(change.pressed)
                                if (!change.pressed) {
                                    val direction = second.up(change.id.value, position)
                                    if (direction != 0) pageSwipe(direction)
                                }
                            }
                        }
                    }
                    // Honor child scrolling/taps until long-press ownership or dragging begins.
                    if (!dragging && !longPressed) {
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        if (final.changes.any { it.id == down.id && it.isConsumed }) break
                    }
                }
            } finally {
                secondaryActive(false)
                if (dragging && currentDrag() != null) cancel()
            }
        }
    }
}

internal fun appDragState(app: AppInfo, origin: DragOrigin, finger: Offset, sizePx: Float) =
    CrossPageDragState(
        item = LayoutItem("app-drag", LauncherPage.PAGE_ID_HOME, ItemType.APP,
            app.packageName, app.activityName, label = app.label, compact = GridPosition(0, 0)),
        sourcePageId = LauncherPage.PAGE_ID_HOME, spanX = 1, spanY = 1,
        itemWidthDp = 56.dp, itemHeightDp = 56.dp, itemWidthPx = sizePx, itemHeightPx = sizePx,
        topLeftInRoot = finger - Offset(sizePx / 2, sizePx / 2), fingerInRoot = finger, origin = origin
    )
