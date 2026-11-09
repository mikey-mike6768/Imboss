package com.imboss.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
        const val EXTRA_TAB = "media_tab"
        private const val CHANNEL_ID = "imboss_media"
        private const val NOTIFICATION_ID = 4107
    }

    private var session: MediaSession? = null
    private var title = "IMBOSS media"
    private var site = "IMBOSS"
    private var playing = false
    private var resumeAfterFocus = false
    private var mediaTab = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeAfterFocus = false
                MainActivity.activeInstance?.controlWebPlayback(false, mediaTab)
                updateState(false)
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterFocus) {
                    resumeAfterFocus = false
                    MainActivity.activeInstance?.controlWebPlayback(true, mediaTab)
                    updateState(true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterFocus = false
                MainActivity.activeInstance?.controlWebPlayback(false, mediaTab)
                updateState(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterFocus = playing && getSharedPreferences("imboss_settings", MODE_PRIVATE).getBoolean("media_resume_focus", false)
                MainActivity.activeInstance?.controlWebPlayback(false, mediaTab)
                updateState(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // WebView does not expose per-element volume to the host safely; pause instead of ducking.
                resumeAfterFocus = playing
                MainActivity.activeInstance?.controlWebPlayback(false, mediaTab)
                updateState(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, noisyFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisyReceiver, noisyFilter)
        }
        session = MediaSession(this, "IMBOSS playback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { requestFocusAndPlay() }
                override fun onPause() {
                    resumeAfterFocus = false
                    MainActivity.activeInstance?.controlWebPlayback(false, mediaTab)
                    updateState(false)
                }
                override fun onStop() {
                    MainActivity.activeInstance?.controlWebPlayback(false, mediaTab)
                    stopSelf()
                }
            })
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MainActivity.activeInstance?.controlWebPlayback(false, mediaTab)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PLAY || intent?.action == ACTION_PAUSE) {
            if (intent.action == ACTION_PLAY) requestFocusAndPlay()
            else {
                resumeAfterFocus = false
                MainActivity.activeInstance?.controlWebPlayback(false, mediaTab)
                updateState(false)
            }
            return START_NOT_STICKY
        }
        title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: title
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        site = android.net.Uri.parse(url).host?.removePrefix("www.") ?: "IMBOSS"
        mediaTab = intent?.getIntExtra(EXTRA_TAB, mediaTab) ?: mediaTab
        startForeground(NOTIFICATION_ID, buildNotification())
        if (intent?.action == ACTION_PLAYING) {
            // Chromium/WebView owns focus for its HTML media pipeline. Do not request
            // a second focus lease here or pause the page when this redundant request fails.
            updateState(true)
        } else updateState(false)
        return START_STICKY
    }

    private fun requestAudioFocusAndPlay() {
        if (requestAudioFocus()) {
            MainActivity.activeInstance?.controlWebPlayback(true, mediaTab)
            updateState(true)
        }
    }

    private fun requestFocusAndPlay() = requestAudioFocusAndPlay()

    private fun requestAudioFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(true)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        focusRequest = null
    }

    private fun updateState(isPlaying: Boolean) {
        playing = isPlaying
        if (playing) {
            if (wakeLock == null) wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IMBOSS:MediaPlayback")
            if (wakeLock?.isHeld != true) wakeLock?.acquire(4 * 60 * 60 * 1000L)
        } else {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            abandonAudioFocus()
        }
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session?.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, site)
                .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, "IMBOSS")
                .build()
        )
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
                .build()
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val icon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val label = if (playing) "Pause" else "Play"
        val action = Intent(this, MediaPlaybackService::class.java).apply {
            this.action = if (playing) ACTION_PAUSE else ACTION_PLAY
        }
        val control = PendingIntent.getService(this, 1, action,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("$site • IMBOSS")
            .setContentIntent(launch)
            .setVisibility(if (getSharedPreferences("imboss_settings", MODE_PRIVATE).getBoolean("media_lock_controls", true)) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
            .setOngoing(playing)
            .apply {
                if (getSharedPreferences("imboss_settings", MODE_PRIVATE).getBoolean("media_notifications", true)) {
                    addAction(icon, label, control)
                }
                if (getSharedPreferences("imboss_settings", MODE_PRIVATE).getBoolean("media_lock_controls", true)) {
                    setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0))
                }
            }.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Media playback", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Playback controls for media in IMBOSS"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        abandonAudioFocus()
        session?.isActive = false
        session?.release()
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
