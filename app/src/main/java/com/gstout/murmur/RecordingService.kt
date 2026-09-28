package com.gstout.murmur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import java.io.File

/**
 * Foreground service that owns the microphone while dictating. Android only lets an app
 * record from the background while it has a microphone-type foreground service running.
 */
class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseRecorder()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun start() {
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            val file = File(cacheDir, "dictation.m4a").apply { delete() }
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setMaxDuration(MAX_DURATION_MS)
            r.setOutputFile(file.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finish(deliver = true)
            }
            r.prepare()
            r.start()
            recorder = r
            outputFile = file
            // The user may have tapped stop before this service finished starting.
            pendingFinish?.let { deliver -> pendingFinish = null; finish(deliver) }
        } catch (e: Exception) {
            releaseRecorder()
            stopSelfCompletely()
            DictationAccessibilityService.instance?.onRecordingFailed("Couldn't start the mic: ${e.message}")
        }
    }

    /** Stop recording. When [deliver] is true the audio is handed off for transcription. */
    private fun finish(deliver: Boolean) {
        val r = recorder ?: return
        recorder = null
        // stop() throws if almost nothing was recorded (e.g. an instant double-tap).
        val stoppedCleanly = try {
            r.stop(); true
        } catch (e: RuntimeException) {
            false
        }
        r.release()
        stopSelfCompletely()

        if (!deliver) return
        val file = outputFile
        val service = DictationAccessibilityService.instance
        if (stoppedCleanly && file != null && file.length() > 0) {
            service?.onAudioReady(file)
        } else {
            service?.onRecordingFailed("Recording was too short")
        }
    }

    private fun releaseRecorder() {
        recorder?.let {
            try { it.stop() } catch (_: RuntimeException) {}
            it.release()
        }
        recorder = null
    }

    private fun stopSelfCompletely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dictation", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Murmur is listening")
            .setContentText("Tap the red button to finish")
            .setOngoing(true)
            .build()
    }

    companion object {
        @Volatile
        var instance: RecordingService? = null
            private set

        /** Set when stop/cancel is requested before the service is up. */
        @Volatile
        private var pendingFinish: Boolean? = null

        fun begin() {
            pendingFinish = null
        }

        /** Stop recording; [deliver] = false discards the audio. */
        fun end(deliver: Boolean) {
            val service = instance
            if (service?.recorder != null) service.finish(deliver) else pendingFinish = deliver
        }

        private const val CHANNEL_ID = "dictation"
        private const val NOTIFICATION_ID = 1
        private const val MAX_DURATION_MS = 10 * 60 * 1000
    }
}
