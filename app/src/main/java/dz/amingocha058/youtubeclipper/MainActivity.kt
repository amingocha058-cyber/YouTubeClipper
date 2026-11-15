package dz.amingocha058.youtubeclipper

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ClipperScreen()
                }
            }
        }
    }
}

@Composable
private fun ClipperScreen() {
    var videoUrl by rememberSaveable { mutableStateOf("") }
    var selectedVideoName by rememberSaveable { mutableStateOf("") }
    var clipLength by rememberSaveable { mutableStateOf("30") }
    var aspectRatio by rememberSaveable { mutableStateOf("Original 16:9") }
    var visibility by rememberSaveable { mutableStateOf("Unlisted") }
    var status by rememberSaveable { mutableStateOf("Choose a local video to get started.") }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoName = uri.lastPathSegment ?: uri.toString()
            status = "Video selected. Clip processing will be added in the next stage."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("YouTube Clipper", style = MaterialTheme.typography.headlineMedium)
        Text("Prepare short clips for your YouTube channel")

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Video source", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("YouTube URL (planned)") },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") },
                    singleLine = true
                )
                OutlinedButton(
                    onClick = { videoPicker.launch(arrayOf("video/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose video from device") }
                if (selectedVideoName.isNotBlank()) Text("Selected: $selectedVideoName")
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Clip duration", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChoiceRadio("30 seconds", clipLength == "30") { clipLength = "30" }
                    ChoiceRadio("60 seconds", clipLength == "60") { clipLength = "60" }
                }
                Text("Custom start/end time will be added in the editing stage.")
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Output format", style = MaterialTheme.typography.titleMedium)
                ChoiceRadio("Original 16:9", aspectRatio == "Original 16:9") { aspectRatio = "Original 16:9" }
                ChoiceRadio("Vertical Shorts 9:16", aspectRatio == "Vertical Shorts 9:16") { aspectRatio = "Vertical Shorts 9:16" }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("4. YouTube privacy", style = MaterialTheme.typography.titleMedium)
                ChoiceRadio("Private", visibility == "Private") { visibility = "Private" }
                ChoiceRadio("Unlisted", visibility == "Unlisted") { visibility = "Unlisted" }
                ChoiceRadio("Public", visibility == "Public") { visibility = "Public" }
            }
        }

        Button(
            onClick = {
                status = when {
                    selectedVideoName.isBlank() && videoUrl.isBlank() ->
                        "Select a local video first. YouTube URL processing is not implemented yet."
                    selectedVideoName.isNotBlank() ->
                        "Settings selected. Video processing is the next development step."
                    else -> "Direct processing of YouTube URLs is not available yet."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Prepare clip") }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(status)
            }
        }

        Text(
            "Early development build — clipping, OAuth sign-in and YouTube upload are not implemented yet.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ChoiceRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
