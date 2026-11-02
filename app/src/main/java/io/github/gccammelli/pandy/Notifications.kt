package io.github.gccammelli.pandy

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.gccammelli.pandy.ui.printer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val ALERTS = "alerts"
private const val ALERTS_CRITICAL = "alerts_critical"
private const val WATCH = "watch"

const val ACTION_PRINTER_ACTION = "io.github.gccammelli.pandy.PRINTER_ACTION"
const val EXTRA_SERIAL = "serial"
const val EXTRA_ACTION = "action"
const val EXTRA_NOTIF_ID = "notif_id"

private fun Context.notifications() = getSystemService(NotificationManager::class.java).also { nm ->
    nm.createNotificationChannel(NotificationChannel(ALERTS, "Print alerts", NotificationManager.IMPORTANCE_HIGH))
    val critical = NotificationChannel(ALERTS_CRITICAL, "Critical printer alerts", NotificationManager.IMPORTANCE_HIGH).apply {
        enableVibration(true)
    }
    nm.createNotificationChannel(critical)
    nm.createNotificationChannel(NotificationChannel(WATCH, "Background connection", NotificationManager.IMPORTANCE_LOW))
}

private fun Context.openPandy() = PendingIntent.getActivity(
    this, 0,
    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

fun Context.canNotify() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun actionIntent(ctx: Context, serial: String, action: String, notifId: Int): PendingIntent {
    val intent = Intent(ctx, NotificationActionReceiver::class.java).apply {
        this.action = ACTION_PRINTER_ACTION
        putExtra(EXTRA_SERIAL, serial)
        putExtra(EXTRA_ACTION, action)
        putExtra(EXTRA_NOTIF_ID, notifId)
    }
    val reqCode = (serial.hashCode() * 31) + action.hashCode()
    return PendingIntent.getBroadcast(ctx, reqCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** Handles user actions from notifications (Pause, Resume, Stop). */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serial = intent.getStringExtra(EXTRA_SERIAL) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (action == "stop" && notifId != -1) {
            context.getSystemService(NotificationManager::class.java).cancel(notifId)
        }
        Repo.actPrinter(serial, action)
    }
}

class AndroidNotifier(private val ctx: Context) : Notifier {
    override fun show(key: String, title: String, text: String) {
        showEvent(PrintEvent(title, text, serial = key))
    }

    override fun showEvent(event: PrintEvent) {
        if (!ctx.canNotify()) return
        val nm = ctx.notifications()
        val notifId = (event.serial ?: event.title).hashCode()
        val isCritical = event.type == PrintEventType.ATTENTION || event.type == PrintEventType.FAILED
        val channel = if (isCritical) ALERTS_CRITICAL else ALERTS

        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(event.title)
            .setContentText(event.text)
            .setContentIntent(ctx.openPandy())
            .setAutoCancel(true)
            .setCategory(if (isCritical) Notification.CATEGORY_ALARM else Notification.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        for (action in event.actions) {
            builder.addAction(R.drawable.ic_stat, action.label, actionIntent(ctx, event.serial.orEmpty(), action.id, notifId))
        }

        nm.notify(notifId, builder.build())

        val imgPath = event.imagePath
        if (imgPath != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val bytes = runCatching { Repo.ha?.bytes(imgPath) }.getOrNull()
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        builder.setLargeIcon(bitmap)
                        builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap).bigLargeIcon(null as Bitmap?))
                        nm.notify(notifId, builder.build())
                    }
                }
            }
        }
    }
}

/**
 * Keeps Pandy connected with the screen off so [Repo] can send print alerts. Its ongoing notification shows the
 * printer's state. Stops itself when the user disconnects.
 * shortcut: not restarted after a reboot, so open Pandy once after one; add a boot receiver if people ask.
 */
class WatchService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job != null) return START_STICKY
        val nm = notifications()

        fun note(text: String, p: Printer? = null): Notification {
            val builder = NotificationCompat.Builder(this, WATCH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Pandy")
                .setContentText(text)
                .setContentIntent(openPandy())
                .setOngoing(true)
                .setOnlyAlertOnce(true)

            if (p != null && p.printing) {
                builder.setProgress(100, p.progress, false)
                if (p.paused) {
                    builder.addAction(R.drawable.ic_stat, "Resume", actionIntent(this, p.serial, "resume", 1))
                } else {
                    builder.addAction(R.drawable.ic_stat, "Pause", actionIntent(this, p.serial, "pause", 1))
                }
                builder.addAction(R.drawable.ic_stat, "Stop", actionIntent(this, p.serial, "stop", 1))
            }
            return builder.build()
        }

        ServiceCompat.startForeground(this, 1, note("Watching your printer"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        Repo.start()
        job = CoroutineScope(Dispatchers.Main).launch {
            Repo.state.map { it.printer() }.distinctUntilChanged().collect { p ->
                val text = when {
                    !Repo.state.value.on -> null
                    p == null -> "Connecting…"
                    p.printing -> "${p.name}: ${p.status} ${p.progress}%"
                    else -> "${p.name}: ${if (p.online) "idle" else "offline"}"
                }
                if (text == null) stopSelf() else nm.notify(1, note(text, p))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        if (!Repo.visible && !Repo.watching) Repo.stop() // nothing else is using the connection
        super.onDestroy()
    }
}
