package com.nuvio.app.features.extras

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nuvio.app.features.settings.AppIconPlatform

internal actual object PartyInviteNotifier {
    private const val CHANNEL_ID = "party_friend_invites"
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                // High importance so the invite pops up as a heads-up notification.
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Watch party invites", NotificationManager.IMPORTANCE_HIGH),
                )
            }
        }
    }

    /** Returns false when notifications are not allowed, so the app can show the invite itself. */
    actual suspend fun show(invite: PartyInvite): Boolean {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return false
        val intent = Intent().apply {
            component = AppIconPlatform.currentLauncherComponent(context)
            action = Intent.ACTION_VIEW
            data = Uri.parse("nuvio://party/${invite.partyCode}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            invite.partyCode.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val sender = invite.senderName.ifBlank { "Someone" }
        val what = invite.title.ifBlank { "a watch party" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setContentTitle("Watch party invite")
            .setContentText(
                if (invite.knownFriend) "$sender invites you to watch $what. Tap to join."
                else "$sender (not in your friends) invites you to watch $what. Tap to join.",
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        return runCatching {
            notifications.notify(invite.partyCode.hashCode(), notification)
            true
        }.getOrDefault(false)
    }
}
