package eu.emufii.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.emufii.app.MainActivity
import eu.emufii.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Notifications {

    const val CHANNEL_FRIENDS = "emufii_friends"

    private const val ID_FRIEND_BASE = 5000

    const val EXTRA_OPEN = "eu.emufii.app.notify.OPEN"
    const val OPEN_FRIENDS = "friends"

    object PendingOpen {
        private val _target = MutableStateFlow<String?>(null)
        val target: StateFlow<String?> = _target.asStateFlow()

        fun offer(intent: Intent?) {
            intent?.getStringExtra(EXTRA_OPEN)?.let { _target.value = it }
            // A resumed activity keeps its starting intent; without this every rotation reopens friends.
            intent?.removeExtra(EXTRA_OPEN)
        }

        fun consume(): String? = _target.value.also { _target.value = null }
    }

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FRIENDS,
                context.getString(R.string.notify_channel_friends),
                // Default and not high: no banner over whatever the player is doing.
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notify_channel_friends_desc) }
        )
    }

    fun allowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun friendEvent(context: Context, event: FriendEvent) {
        val name = event.name ?: context.getString(R.string.notify_friend_unnamed)
        val text = when (event) {
            is FriendEvent.CameOnline -> context.getString(R.string.notify_friend_online, name)
            is FriendEvent.StartedPlaying -> event.game
                ?.let { context.getString(R.string.notify_friend_playing, name, it) }
                ?: context.getString(R.string.notify_friend_in_game, name)
        }
        post(
            context = context,
            id = ID_FRIEND_BASE + (event.code.hashCode() and 0x3ff),
            channel = CHANNEL_FRIENDS,
            icon = R.drawable.ic_notify_friend,
            title = context.getString(R.string.app_name),
            text = text,
            open = OPEN_FRIENDS
        )
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        icon: Int,
        title: String,
        text: String,
        open: String?
    ) {
        if (!allowed(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { open?.let { putExtra(EXTRA_OPEN, it) } }

        val pending = PendingIntent.getActivity(
            context,
            // Distinct per notification, or Android reuses the first one's extras.
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
