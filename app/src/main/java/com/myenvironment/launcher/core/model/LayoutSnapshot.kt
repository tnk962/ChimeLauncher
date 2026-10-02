package com.myenvironment.launcher.core.model

/** 設定を含まない、Undo用の一貫したレイアウト状態。 */
data class LayoutSnapshot(
    val userPages: List<LauncherPage> = emptyList(),
    val items: List<LayoutItem> = emptyList(),
    val dockItems: List<DockItem> = emptyList()
) {
    val widgetIds: Set<Int> get() = items
        .filter { it.type == ItemType.WIDGET && it.appWidgetId > 0 }
        .mapTo(mutableSetOf()) { it.appWidgetId }

    fun normalized(): LayoutSnapshot = copy(
        userPages = userPages.sortedWith(compareBy({ it.sortOrder }, { it.id })),
        items = items.sortedBy { it.id },
        dockItems = dockItems.sortedWith(compareBy({ it.positionIndex }, { it.id }))
    )
}
