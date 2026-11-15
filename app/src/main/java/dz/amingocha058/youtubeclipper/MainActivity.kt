package dz.amingocha058.youtubeclipper

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Context
import android.content.ContentValues
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var authorizationCallback: ((Result<String>) -> Unit)? = null
    private lateinit var authorizationLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authorizationLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val callback = authorizationCallback
            authorizationCallback = null
            try {
                val data = result.data ?: error("لم يتم استلام نتيجة التفويض")
                val authorization = Identity.getAuthorizationClient(this)
                    .getAuthorizationResultFromIntent(data)
                val token = authorization.accessToken ?: error("لم يتم الحصول على رمز الوصول")
                callback?.invoke(Result.success(token))
            } catch (error: Exception) {
                callback?.invoke(Result.failure(error))
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ClipperScreen(
                        onAuthorize = { callback -> authorizeYouTube(callback) }
                    )
                }
            }
        }
    }

    private fun authorizeYouTube(callback: (Result<String>) -> Unit) {
        authorizationCallback = callback
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(Scope("https://www.googleapis.com/auth/youtube.upload"))
            )
            .build()

        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        authorizationCallback = null
                        callback(Result.failure(IllegalStateException("تعذر بدء نافذة موافقة Google")))
                    } else {
                        authorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    authorizationCallback = null
                    val token = result.accessToken
                    if (token.isNullOrBlank()) callback(Result.failure(IllegalStateException("رمز الوصول فارغ")))
                    else callback(Result.success(token))
                }
            }
            .addOnFailureListener { error ->
                authorizationCallback = null
                callback(Result.failure(error))
            }
    }
}

@Composable
private fun ClipperScreen(onAuthorize: ((Result<String>) -> Unit) -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()

    var sourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceName by rememberSaveable { mutableStateOf("") }
    var durationMs by rememberSaveable { mutableStateOf(0L) }
    var startSeconds by rememberSaveable { mutableStateOf("0") }
    var endSeconds by rememberSaveable { mutableStateOf("30") }
    var quickLength by rememberSaveable { mutableStateOf("30") }
    var aspect by rememberSaveable { mutableStateOf("9:16") }
    var visibility by rememberSaveable { mutableStateOf("unlisted") }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var outputPath by rememberSaveable { mutableStateOf<String?>(null) }
    var galleryUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf("اختر فيديو من هاتفك للبدء.") }
    var busy by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val retriever = MediaMetadataRetriever()
            val length = try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
            sourceUri = uri.toString()
            sourceName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "video"
            durationMs = length
            startSeconds = "0"
            endSeconds = ((length / 1000L).coerceAtLeast(1L)).toString()
            title = buildSuggestedTitle(sourceName, 0L)
            outputPath = null
            status = if (length > 0) "تم اختيار الفيديو. المدة: ${formatTime(length / 1000L)}" else "تم اختيار الفيديو، لكن تعذر قراءة مدته."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("YouTube Clipper", style = MaterialTheme.typography.headlineMedium)
        Text("قص الفيديوهات وتجهيز Shorts ثم رفعها إلى قناتك.")

        var liveRecording by rememberSaveable { mutableStateOf(false) }

        DisposableEffect(context) {
            val savedReceiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val path = intent?.getStringExtra(LiveScreenRecorderService.EXTRA_OUTPUT_PATH) ?: return
                    val file = File(path)
                    if (!file.exists() || file.length() <= 0L) {
                        status = "انتهى التسجيل لكن الملف غير صالح."
                        return
                    }
                    scope.launch {
                        val publicUri = withContext(Dispatchers.IO) {
                            saveVideoToGallery(context, file)
                        }
                        val retriever = MediaMetadataRetriever()
                        val length = try {
                            retriever.setDataSource(file.absolutePath)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        } catch (_: Exception) {
                            0L
                        } finally {
                            try { retriever.release() } catch (_: Exception) { }
                        }
                        sourceUri = Uri.fromFile(file).toString()
                        sourceName = file.name
                        durationMs = length
                        startSeconds = "0"
                        endSeconds = (length / 1000L).coerceAtLeast(1L).toString()
                        quickLength = "30"
                        title = buildSuggestedTitle(file.name, 0L)
                        outputPath = null
                        galleryUriString = publicUri?.toString()
                        liveRecording = false
                        status = when {
                            publicUri != null && length > 0L -> "تم حفظ التسجيل في معرض الهاتف وأصبح جاهزاً للقص: " + formatTime(length / 1000L)
                            publicUri != null -> "تم حفظ التسجيل في المعرض، لكن تعذرت قراءة مدته."
                            length > 0L -> "تم التسجيل، لكن تعذر نسخه إلى معرض الهاتف. الملف متاح داخل التطبيق."
                            else -> "تم التسجيل، لكن تعذرت قراءة مدة الفيديو أو حفظه في المعرض."
                        }
                    }
                }
            }
            val filter = IntentFilter(LiveScreenRecorderService.ACTION_RECORDING_SAVED)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(savedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(savedReceiver, filter)
            }
            onDispose {
                try { context.unregisterReceiver(savedReceiver) } catch (_: Exception) { }
            }
        }
        val screenCaptureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(context, LiveScreenRecorderService::class.java).apply {
                    action = LiveScreenRecorderService.ACTION_START
                    putExtra(LiveScreenRecorderService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(LiveScreenRecorderService.EXTRA_DATA, result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                else context.startService(serviceIntent)
                liveRecording = true
                status = "بدأ تسجيل الشاشة. شغّل بث YouTube بصوت مكبر الهاتف للحصول على صوت عبر الميكروفون."
            } else {
                status = "تم إلغاء إذن تسجيل الشاشة."
            }
        }

        val audioPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
            } else {
                status = "يجب السماح باستخدام الميكروفون لتسجيل الصوت."
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تسجيل بث YouTube", style = MaterialTheme.typography.titleMedium)
                Text("يسجل الشاشة كاملة حالياً، مع صوت الميكروفون. شغّل البث من مكبر الهاتف؛ الصوت الداخلي المباشر غير مضمون.")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!liveRecording) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            context.startService(Intent(context, LiveScreenRecorderService::class.java).apply {
                                action = LiveScreenRecorderService.ACTION_STOP
                            })
                            liveRecording = false
                            status = "تم طلب إيقاف التسجيل؛ جارٍ تجهيز الملف لإضافته إلى محرّر القص."
                        }
                    }
                ) { Text(if (liveRecording) "إيقاف تسجيل البث" else "بدء تسجيل البث") }
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. الفيديو", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("اختيار فيديو من الهاتف")
                }
                if (sourceName.isNotBlank()) Text("الملف: $sourceName")
                if (durationMs > 0) Text("المدة الكاملة: ${formatTime(durationMs / 1000L)}")
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. تحديد الجزء", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChoiceRadio("آخر 30 ثانية", quickLength == "30") { quickLength = "30"; endSeconds = (durationMs / 1000L).toString(); startSeconds = ((durationMs / 1000L - 30).coerceAtLeast(0)).toString() }
                    ChoiceRadio("آخر 60 ثانية", quickLength == "60") { quickLength = "60"; endSeconds = (durationMs / 1000L).toString(); startSeconds = ((durationMs / 1000L - 60).coerceAtLeast(0)).toString() }
                }
                OutlinedTextField(
                    value = startSeconds, onValueChange = { startSeconds = it.filter(Char::isDigit) },
                    label = { Text("بداية المقطع بالثواني") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = endSeconds, onValueChange = { endSeconds = it.filter(Char::isDigit) },
                    label = { Text("نهاية المقطع بالثواني") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("يمكنك تغيير البداية والنهاية يدوياً.")
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. شكل الفيديو", style = MaterialTheme.typography.titleMedium)
                ChoiceRadio("Shorts عمودي 9:16 (قص مركزي)", aspect == "9:16") { aspect = "9:16" }
                ChoiceRadio("أفقي 16:9", aspect == "16:9") { aspect = "16:9" }
            }
        }

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("4. العنوان والوصف", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان الفيديو") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("الوصف (اختياري)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedButton(onClick = {
                    val start = startSeconds.toLongOrNull() ?: 0L
                    title = buildSuggestedTitle(sourceName.ifBlank { "Video" }, start)
                    status = "تم إنشاء عنوان مقترح، ويمكنك تعديله."
                }, modifier = Modifier.fillMaxWidth()) { Text("توليد عنوان تلقائي") }
            }
        }

        Button(
            enabled = sourceUri != null && !busy,
            onClick = {
                val uri = sourceUri?.let(Uri::parse) ?: return@Button
                val start = startSeconds.toLongOrNull() ?: -1L
                val end = endSeconds.toLongOrNull() ?: -1L
                val totalSeconds = durationMs / 1000L
                if (start < 0 || end <= start || end > totalSeconds) {
                    status = "تحقق من البداية والنهاية. يجب أن تكون النهاية أكبر من البداية وضمن مدة الفيديو."
                    return@Button
                }
                busy = true
                status = "جارٍ تجهيز الفيديو..."
                exportVideo(
                    context = context,
                    uri = uri,
                    startMs = start * 1000L,
                    endMs = end * 1000L,
                    vertical = aspect == "9:16",
                    onComplete = { file ->
                        scope.launch {
                            val publicUri = withContext(Dispatchers.IO) {
                                saveVideoToGallery(context, file)
                            }
                            busy = false
                            outputPath = file.absolutePath
                            galleryUriString = publicUri?.toString()
                            status = if (publicUri != null) {
                                "اكتمل التصدير وحُفظ الفيديو في معرض الهاتف: Movies/YouTubeClipper"
                            } else {
                                "اكتمل التصدير، لكن تعذر حفظ نسخة في المعرض. الملف متاح للمشاركة من التطبيق."
                            }
                            if (title.isBlank()) title = buildSuggestedTitle(sourceName, start)
                        }
                    },
                    onError = { error ->
                        busy = false
                        status = "فشل التصدير: ${error.message ?: "خطأ غير معروف"}"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "جارٍ المعالجة..." else "قص وتصدير الفيديو") }

        if (outputPath != null) {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("5. الفيديو الجاهز", style = MaterialTheme.typography.titleMedium)
                    Text(outputPath!!.substringAfterLast('/'))
                    OutlinedButton(onClick = {
                        val file = File(outputPath!!)
                        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "مشاركة الفيديو"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("مشاركة الفيديو") }

                    Text("6. رفع إلى YouTube", style = MaterialTheme.typography.titleMedium)
                    ChoiceRadio("خاص", visibility == "private") { visibility = "private" }
                    ChoiceRadio("غير مدرج", visibility == "unlisted") { visibility = "unlisted" }
                    ChoiceRadio("عام", visibility == "public") { visibility = "public" }
                    Button(
                        enabled = !busy && activity != null,
                        onClick = {
                            if (title.isBlank()) {
                                status = "أدخل عنوان الفيديو قبل الرفع."
                            } else {
                                busy = true
                                status = "بانتظار موافقة حساب Google..."
                                onAuthorize { authResult ->
                                    authResult.onSuccess { token ->
                                        scope.launch {
                                            status = "جارٍ رفع الفيديو إلى YouTube..."
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    YouTubeUploader.upload(
                                                        file = File(outputPath!!),
                                                        accessToken = token,
                                                        title = title,
                                                        description = description,
                                                        privacy = visibility
                                                    )
                                                }
                                            }
                                            busy = false
                                            status = result.fold(
                                                onSuccess = { "تم رفع الفيديو. رابط المشاهدة: https://www.youtube.com/watch?v=$it" },
                                                onFailure = { "فشل الرفع: ${it.message ?: "خطأ غير معروف"}" }
                                            )
                                        }
                                    }.onFailure {
                                        busy = false
                                        status = "تعذر ربط YouTube: ${it.message ?: "تم إلغاء التفويض"}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ربط Google ورفع الفيديو") }
                }
            }
        }

        Card {
            Column(Modifier.padding(14.dp)) {
                Text("الحالة", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(status)
            }
        }
        Text(
            "ملاحظة: الرفع الرسمي يحتاج تفعيل YouTube Data API وإعداد OAuth للتطبيق في Google Cloud. لا تدخل كلمة مرور Google داخل التطبيق.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Publishes a completed MP4 in the public Movies/YouTubeClipper collection.
 * MediaStore exposes it to Gallery/Photos without broad storage permissions.
 */
private fun saveVideoToGallery(context: Context, source: File): Uri? {
    if (!source.exists() || source.length() <= 0L) return null

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, source.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(
            MediaStore.Video.Media.RELATIVE_PATH,
            "${android.os.Environment.DIRECTORY_MOVIES}/YouTubeClipper"
        )
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val destination = resolver.insert(collection, values) ?: return null

    return try {
        resolver.openOutputStream(destination, "w")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("تعذر فتح ملف المعرض للكتابة")

        val completedValues = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        resolver.update(destination, completedValues, null, null)
        destination
    } catch (_: Exception) {
        try { resolver.delete(destination, null, null) } catch (_: Exception) { }
        null
    }
}

@OptIn(UnstableApi::class)
private fun exportVideo(
    context: android.content.Context,
    uri: Uri,
    startMs: Long,
    endMs: Long,
    vertical: Boolean,
    onComplete: (File) -> Unit,
    onError: (Exception) -> Unit
) {
    try {
        val outputDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "YouTubeClipper")
        if (!outputDir.exists() && !outputDir.mkdirs()) error("تعذر إنشاء مجلد التصدير")
        val output = File(outputDir, "clip_${System.currentTimeMillis()}.mp4")
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()
        val mediaItem = MediaItem.Builder().setUri(uri).setClippingConfiguration(clipping).build()
        val effects = if (vertical) {
            androidx.media3.transformer.Effects(
                emptyList(),
                listOf(Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            )
        } else {
            androidx.media3.transformer.Effects(emptyList(), emptyList())
        }
        val edited = EditedMediaItem.Builder(mediaItem).setEffects(effects).build()
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onComplete(output)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (output.exists()) output.delete()
                    onError(exportException)
                }
            })
            .build()
        transformer.start(edited, output.absolutePath)
    } catch (error: Exception) {
        onError(error)
    }
}

private object YouTubeUploader {
    fun upload(file: File, accessToken: String, title: String, description: String, privacy: String): String {
        require(file.exists() && file.length() > 0L) { "ملف الفيديو غير موجود أو فارغ" }
        val metadata = JSONObject()
            .put("snippet", JSONObject()
                .put("title", title.take(100))
                .put("description", description.take(5000))
                .put("categoryId", "22"))
            .put("status", JSONObject().put("privacyStatus", privacy))

        val init = URL("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .openConnection() as HttpURLConnection
        init.requestMethod = "POST"
        init.doOutput = true
        init.connectTimeout = 30_000
        init.readTimeout = 60_000
        init.setRequestProperty("Authorization", "Bearer $accessToken")
        init.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        init.setRequestProperty("X-Upload-Content-Type", "video/mp4")
        init.setRequestProperty("X-Upload-Content-Length", file.length().toString())
        init.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
        val initCode = init.responseCode
        if (initCode !in 200..299) {
            val message = init.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $initCode"
            init.disconnect()
            error("تعذر بدء الرفع: $message")
        }
        val uploadUrl = init.getHeaderField("Location") ?: run {
            init.disconnect()
            error("لم يُرجع YouTube رابط الرفع")
        }
        init.disconnect()

        val upload = URL(uploadUrl).openConnection() as HttpURLConnection
        upload.requestMethod = "PUT"
        upload.doOutput = true
        upload.connectTimeout = 30_000
        upload.readTimeout = 180_000
        upload.setRequestProperty("Authorization", "Bearer $accessToken")
        upload.setRequestProperty("Content-Type", "video/mp4")
        upload.setFixedLengthStreamingMode(file.length())
        file.inputStream().use { input ->
            upload.outputStream.use { output ->
                input.copyTo(output)
            }
        }
        val code = upload.responseCode
        val response = (if (code in 200..299) upload.inputStream else upload.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        upload.disconnect()
        if (code !in 200..299) error("رفض YouTube الفيديو: $response")
        return JSONObject(response).optString("id").takeIf { it.isNotBlank() }
            ?: error("تم الرد دون معرّف للفيديو")
    }
}

private fun buildSuggestedTitle(fileName: String, startSeconds: Long): String {
    val cleanName = fileName.substringBeforeLast('.').replace('_', ' ').take(65)
    return "$cleanName | Clip ${formatTime(startSeconds)}".take(100)
}

private fun formatTime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d", safe / 60, safe % 60)
}

@Composable
private fun ChoiceRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
