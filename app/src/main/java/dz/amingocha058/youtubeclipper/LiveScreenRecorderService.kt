package dz.amingocha058.youtubeclipper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Environment
import androidx.core.app.NotificationCompat
import java.io.File

class LiveScreenRecorderService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START -> startRecording(intent, startId)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent, startId: Int) {
        try {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("YouTube Clipper")
                .setContentText("تسجيل الشاشة جارٍ")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            @Suppress("DEPRECATION")
            val data = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            } else {
                intent.getParcelableExtra(EXTRA_DATA)
            } ?: error("لم يصل إذن تسجيل الشاشة")

            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "YouTubeClipper")
            if (!dir.exists() && !dir.mkdirs()) error("تعذر إنشاء مجلد التسجيل")
            outputFile = File(dir, "live_${System.currentTimeMillis()}.mp4")

            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile!!.absolutePath)
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(6_000_000)
                setVideoFrameRate(30)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                prepare()
            }
            val surface = recorder!!.surface
            virtualDisplay = projection!!.createVirtualDisplay(
                "YouTubeClipperLive", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )
            recorder!!.start()
        } catch (error: Exception) {
            stopRecording()
            stopSelf(startId)
        }
    }

    private fun stopRecording() {
        try { recorder?.stop() } catch (_: Exception) {
            outputFile?.delete()
        }
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) { }
        recorder = null
        try { virtualDisplay?.release() } catch (_: Exception) { }
        virtualDisplay = null
        try { projection?.stop() } catch (_: Exception) { }
        projection = null

        val completedFile = outputFile
        if (completedFile != null && completedFile.exists() && completedFile.length() > 0L) {
            sendBroadcast(Intent(ACTION_RECORDING_SAVED).apply {
                setPackage(packageName)
                putExtra(EXTRA_OUTPUT_PATH, completedFile.absolutePath)
            })
        }
        outputFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "تسجيل البث", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { recorder?.release() } catch (_: Exception) { }
        try { virtualDisplay?.release() } catch (_: Exception) { }
        try { projection?.stop() } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dz.amingocha058.youtubeclipper.START_LIVE_RECORDING"
        const val ACTION_STOP = "dz.amingocha058.youtubeclipper.STOP_LIVE_RECORDING"
        const val ACTION_RECORDING_SAVED = "dz.amingocha058.youtubeclipper.LIVE_RECORDING_SAVED"
        const val EXTRA_OUTPUT_PATH = "output_path"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        private const val CHANNEL_ID = "live_screen_recording"
        private const val NOTIFICATION_ID = 2401
    }
}
