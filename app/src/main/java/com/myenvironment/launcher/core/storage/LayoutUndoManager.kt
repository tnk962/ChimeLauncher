package com.myenvironment.launcher.core.storage

import com.myenvironment.launcher.core.model.LayoutSnapshot
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LayoutUndoState(
    val isEditing: Boolean = false,
    val count: Int = 0,
    val isBusy: Boolean = false
)

/**
 * 編集とUndoを直列化する。履歴はメモリ内のみで、設定は復元しない。
 * 現在の配置・履歴・バインド待ちのどこにもないWidget IDだけを解放する。
 */
class LayoutUndoManager(
    private val readSnapshot: suspend () -> LayoutSnapshot,
    private val restoreSnapshot: suspend (LayoutSnapshot) -> Unit,
    private val releaseWidgetId: (Int) -> Unit,
    private val protectedWidgetIds: () -> Set<Int> = { emptySet() },
    private val historyLimit: Int = 20
) {
    private data class Entry(val before: LayoutSnapshot, val after: LayoutSnapshot)
    private val mutex = Mutex()
    private val history = ArrayDeque<Entry>()
    private val managedWidgetIds = mutableSetOf<Int>()
    private var isEditing = false
    private val mutableState = MutableStateFlow(LayoutUndoState())
    val state: StateFlow<LayoutUndoState> = mutableState.asStateFlow()

    init { require(historyLimit > 0) }

    private suspend fun snapshot() = readSnapshot().normalized()

    private suspend fun <T> serialized(action: suspend () -> T): T = mutex.withLock {
        publish(isBusy = true)
        try { action() } finally { publish(isBusy = false) }
    }

    private fun publish(isBusy: Boolean) {
        mutableState.value = LayoutUndoState(isEditing, history.size, isBusy)
    }

    suspend fun initialize(existingWidgetIds: Set<Int>) = serialized {
        managedWidgetIds.addAll(existingWidgetIds)
        releaseUnusedWidgets(snapshot())
    }

    suspend fun beginSession() = serialized {
        if (!isEditing) {
            history.clear()
            releaseUnusedWidgets(snapshot())
            isEditing = true
        }
    }

    suspend fun endSession() = serialized {
        isEditing = false
        history.clear()
        releaseUnusedWidgets(snapshot())
    }

    suspend fun <T> edit(action: suspend (LayoutSnapshot) -> T): T = serialized {
        val before = snapshot()
        // 外部更新があれば古い履歴で上書きしない。
        if (history.lastOrNull()?.after?.let { it != before } == true) history.clear()
        managedWidgetIds.addAll(before.widgetIds)
        try {
            val result = action(before)
            val after = snapshot()
            managedWidgetIds.addAll(after.widgetIds)
            if (isEditing && before != after) {
                history.addLast(Entry(before, after))
                while (history.size > historyLimit) history.removeFirst()
            }
            releaseUnusedWidgets(after)
            result
        } catch (failure: Exception) {
            // スワップやページ作成＋移動の途中で失敗しても部分変更を残さない。
            withContext(NonCancellable) {
                try {
                    managedWidgetIds.addAll(snapshot().widgetIds)
                    restoreSnapshot(before)
                    releaseUnusedWidgets(before)
                } catch (rollbackFailure: Exception) {
                    history.clear()
                    failure.addSuppressed(rollbackFailure)
                }
            }
            throw failure
        }
    }

    /** バックアップ復元・再バインド等、Undo対象外の更新も編集と直列化する。 */
    suspend fun <T> withoutHistory(action: suspend (LayoutSnapshot) -> T): T = serialized {
        history.clear()
        val before = snapshot()
        managedWidgetIds.addAll(before.widgetIds)
        try { action(before) } finally {
            val after = snapshot()
            managedWidgetIds.addAll(after.widgetIds)
            releaseUnusedWidgets(after)
        }
    }

    suspend fun undo(canUndo: suspend () -> Boolean = { true }): LayoutSnapshot? = serialized {
        if (!isEditing || !canUndo()) return@serialized null
        val entry = history.lastOrNull() ?: return@serialized null
        val current = snapshot()
        if (current != entry.after) {
            history.clear()
            releaseUnusedWidgets(current)
            return@serialized null
        }
        // 復元に失敗した場合は履歴を残し、再試行できるようにする。
        restoreSnapshot(entry.before)
        history.removeLast()
        managedWidgetIds.addAll(current.widgetIds)
        releaseUnusedWidgets(entry.before)
        entry.before
    }

    private fun releaseUnusedWidgets(current: LayoutSnapshot) {
        val reachable = current.widgetIds + history.flatMap { it.before.widgetIds + it.after.widgetIds } +
            protectedWidgetIds()
        val unused = managedWidgetIds - reachable
        unused.forEach { id ->
            releaseWidgetId(id)
            managedWidgetIds.remove(id)
        }
    }
}
