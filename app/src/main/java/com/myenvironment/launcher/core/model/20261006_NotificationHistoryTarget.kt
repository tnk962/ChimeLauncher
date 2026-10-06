package com.myenvironment.launcher.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GalaxyNotificationHistoryTarget(val title: String) {
    NOTISTAR("NotiStar優先"), SYSTEM("システム標準")
}

internal enum class NotificationHistoryDestination { NOTISTAR, GOODPIXEL, SYSTEM }

internal fun notificationHistoryDestinations(manufacturer: String, target: GalaxyNotificationHistoryTarget): List<NotificationHistoryDestination> =
    if (manufacturer.trim().equals("samsung", ignoreCase = true)) {
        if (target == GalaxyNotificationHistoryTarget.SYSTEM) listOf(NotificationHistoryDestination.SYSTEM)
        else listOf(NotificationHistoryDestination.NOTISTAR, NotificationHistoryDestination.SYSTEM)
    } else listOf(NotificationHistoryDestination.GOODPIXEL, NotificationHistoryDestination.SYSTEM)
