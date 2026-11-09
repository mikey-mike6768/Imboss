package com.imboss.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class MediaPlaybackService : Service() {
    companion object {
        const val ACTION_PLAYING = "com.imboss.browser.MEDIA_PLAYING"
        const val ACTION_PAUSED = "com.imboss.browser.MEDIA_PAUSED"
        const val ACTION_STOP = "com.imboss.browser.MEDIA_STOP"
        const val ACTION_PLAY = "com.imboss.browser.MEDIA_ACTION_PLAY"
        const val ACTION_PAUSE = "com.imboss.browser.MEDIA_ACTION_PAUSE"
        const val EXTRA_TITLE = "media_title"
        const val EXTRA_URL = "media_url"
        private const val CHANNEL_ID = "imboss_media"
        private const val NOTIFICATION_ID = 4107
    }

    private var session: MediaSession? = null
    private var title = "IMBOSS media"
    private var playing = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSession(this, "IMBOSS playback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    MainActivity.activeInstance?.controlWebPlayback(true)
                    updateState(true)
                }
                override fun onPause() {
                    MainActivity.activeInstance?.controlWebPlayback(false)
                    updateState(false)
                }
                override fun onStop() {
                    MainActivity.activeInstance?.controlWebPlayback(false)
                    stopSelf()
                }
            })
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PLAY || intent?.action == ACTION_PAUSE) {
            val wantsPlay = intent.action == ACTION_PLAY
            MainActivity.activeInstance?.controlWebPlayback(wantsPlay)
            updateState(wantsPlay)
            return START_STICKY
        }
        title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: title
        playing = intent?.action == ACTION_PLAYING
        // Start foreground immediately so Android is allowed to keep the media session alive.
        startForeground(NOTIFICATION_ID, buildNotification())
        updateState(playing)
        return START_STICKY
    }

    private fun updateState(isPlaying: Boolean) {
        playing = isPlaying
        if (playing) {
            if (wakeLock == null) {
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IMBOSS:MediaPlayback")
            }
            if (wakeLock?.isHeld != true) wakeLock?.acquire()
        } else if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session?.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_TITLE, title)
                .putString(android.media.MediaMetadata.METADATA_ARTIST, "Playing in IMBOSS")
                .build()
        )
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
                .build()
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPause = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val action = if (playing) "Pause" else "Play"
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle(title)
                .setContentText("Playing in IMBOSS")
                .setContentIntent(launch)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(playing)
                .addAction(Notification.Action.Builder(playPause, action, mediaActionPendingIntent()).build())
                .setStyle(Notification.MediaStyle().setMediaSession(session!!.sessionToken).setShowActionsInCompactView(0))
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle(title)
                .setContentText("Playing in IMBOSS")
                .setContentIntent(launch)
                .setOngoing(playing)
                .build()
        }
    }

    private fun mediaActionPendingIntent(): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).apply {
            action = if (playing) ACTION_PAUSE else ACTION_PLAY
        }
        return PendingIntent.getService(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Media playback", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Lock-screen controls for audio playing in IMBOSS"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        session?.isActive = false
        session?.release()
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
