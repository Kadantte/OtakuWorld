package com.programmersbox.uiviews.repository

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.programmersbox.favoritesdatabase.ItemDao
import com.programmersbox.favoritesdatabase.NotificationItem

private const val GROUP_ID = 42

class NotificationRepository(
    context: Context,
    private val itemDao: ItemDao,
) {
    private val notificationManager by lazy {
        context.getSystemService<NotificationManager>()
    }

    fun cancelById(id: Int) {
        notificationManager?.cancel(id)
    }

    fun cancelNotification(item: NotificationItem) {
        notificationManager?.cancel(item.id)
        val g = notificationManager
            ?.activeNotifications
            ?.map { it.notification }
            ?.filter { it.group == "otakuGroup" }
            .orEmpty()
        if (g.size == 1) notificationManager?.cancel(GROUP_ID)
    }

    fun cancelGroup() {
        notificationManager?.cancel(GROUP_ID)
    }
}