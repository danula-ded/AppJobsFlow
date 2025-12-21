package com.github.jobsflow.appjobsflow.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.jobsflow.appjobsflow.R

abstract class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    open val channelId: String = "appjobsflow_channel"

    open val notificationChannelImportance: Int = NotificationManager.IMPORTANCE_LOW

    open val notificationCompatPriority: Int = NotificationCompat.PRIORITY_LOW

    protected fun showNotification(message: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)

        manager?.let {
            createNotificationChannel(it)

            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle(applicationContext.getString(R.string.app_name))
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(notificationCompatPriority)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()

            val notificationId = (System.currentTimeMillis() % 2_000_000_000).toInt()

            it.notify(notificationId, notification)
        }
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        if (manager.getNotificationChannel(channelId) != null) {
            return
        }

        val channelName = applicationContext.getString(R.string.app_name)

        NotificationChannel(
            channelId,
            channelName,
            notificationChannelImportance
        ).apply {
            enableVibration(false)
            setShowBadge(true)
        }.also { channel ->
            manager.createNotificationChannel(channel)
        }
    }
}
