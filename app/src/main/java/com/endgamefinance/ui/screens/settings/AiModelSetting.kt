package com.endgamefinance.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.endgamefinance.data.ai.AiModel
import com.endgamefinance.data.ai.ModelState
import com.endgamefinance.ui.theme.Spacing

private fun gb(bytes: Long): String =
    if (bytes <= 0) "?" else String.format("%.2f GB", bytes / 1_000_000_000.0)

@Composable
fun AiModelSetting() {
    val context = LocalContext.current
    val state by AiModel.state.collectAsState()
    var showAdvanced by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(AiModel.DEFAULT_URL) }
    var confirmDelete by remember { mutableStateOf(false) }

    Text(
        "The optional assistant runs Gemma 4 on your device. The model is a " +
            "one-time download — the app's only network use. After it's " +
            "downloaded, the AI works fully offline.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    var build by remember { mutableStateOf(AiModel.selectedBuild(context)) }
    Text("Model build", style = MaterialTheme.typography.bodyLarge)
    com.endgamefinance.ui.components.DropdownField(
        label = "Which build",
        options = listOf(
            AiModel.BUILD_GENERIC to "Generic — 2.6 GB (GPU/CPU, recommended)",
            AiModel.BUILD_NPU_SM8750 to "NPU · Snapdragon 8 Elite — 3.0 GB (unsupported)",
        ),
        selectedId = build,
        onSelect = { picked ->
            picked?.let {
                build = it
                AiModel.setSelectedBuild(context, it)
                com.endgamefinance.data.ai.AiPrefs.setBackend(
                    context,
                    if (it == AiModel.BUILD_NPU_SM8750) com.endgamefinance.data.ai.AiPrefs.NPU
                    else com.endgamefinance.data.ai.AiPrefs.GPU,
                )
            }
        },
    )
    Text(
        "Use the generic build — it runs accelerated on this phone's GPU. The NPU " +
            "build is verified NOT to work on sideloaded installs: Samsung restricts " +
            "direct NPU (Hexagon DSP) access to system apps, and selecting it will " +
            "crash the assistant. Kept only for a possible future Play-delivered build.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when (val s = state) {
        is ModelState.Absent, is ModelState.Failed -> {
            if (s is ModelState.Failed) {
                Text(
                    "Download failed: ${s.message}. Tap to retry — it resumes where it stopped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val sizeLabel = if (build == AiModel.BUILD_NPU_SM8750) "~3.0 GB" else "~2.6 GB"
            Button(
                // Download the SELECTED build (deriving its URL), not a fixed URL —
                // otherwise the NPU build could be labelled but never actually fetched.
                onClick = { AiModel.startDownload(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (s is ModelState.Failed) "Resume download" else "Download model ($sizeLabel)") }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide advanced" else "Advanced: custom model URL")
            }
            if (showAdvanced) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Model .litertlm URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Overrides the selected build above. Use a chip-specific build " +
                        "(e.g. Qualcomm sm8750) for best speed, or the default generic build.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { AiModel.startDownload(context, url.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Download from this URL") }
            }
        }

        is ModelState.Downloading -> {
            val frac = if (s.total > 0) (s.bytes.toFloat() / s.total) else 0f
            LinearProgressIndicator(
                progress = { frac.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (s.total > 0) {
                    "${gb(s.bytes)} of ${gb(s.total)} · ${(frac * 100).toInt()}%"
                } else {
                    "${gb(s.bytes)} downloaded…"
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { AiModel.cancelDownload() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pause") }
            Text(
                "You can leave this screen — the download continues in the background.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is ModelState.Ready -> {
            Text(
                "✓ Model ready (${gb(AiModel.modelFile(context).length())}). The assistant " +
                    "is available and runs offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            val active by com.endgamefinance.data.ai.GemmaEngine.activeBackend.collectAsState()
            active?.let {
                Text(
                    "Currently running on: ${it.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } ?: Text(
                "Run a query to load the model and confirm the backend.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val backend by com.endgamefinance.data.ai.AiPrefs.backend(context).collectAsState()
            Text("Compute backend", style = MaterialTheme.typography.bodyLarge)
            com.endgamefinance.ui.components.DropdownField(
                label = "Runs inference on",
                options = listOf(
                    com.endgamefinance.data.ai.AiPrefs.GPU to "GPU (fast, recommended)",
                    com.endgamefinance.data.ai.AiPrefs.CPU to "CPU (most compatible)",
                ),
                selectedId = backend,
                onSelect = { picked ->
                    picked?.let { com.endgamefinance.data.ai.AiPrefs.setBackend(context, it) }
                },
            )
            Text(
                "GPU runs on this phone's graphics chip via OpenCL — much faster than " +
                    "CPU. If GPU fails to start, the app falls back to CPU automatically. " +
                    "(NPU is not offered: Samsung blocks sideloaded apps from the DSP.)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete model", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the AI model?") },
            text = {
                Text(
                    "Frees ~2.6 GB. The assistant stops working until you download " +
                        "it again. Your financial data is unaffected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { AiModel.delete(context); confirmDelete = false },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
