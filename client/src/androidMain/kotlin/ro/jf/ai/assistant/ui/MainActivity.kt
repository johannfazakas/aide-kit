package ro.jf.ai.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidApp(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AndroidApp(viewModel: AppViewModel) {
    val models by viewModel.models.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    App(
        models.tasks,
        models.chat,
        topBar = {
            TopAppBar(
                title = { Text("aide-kit") },
                actions = {
                    TextButton(onClick = { showSettings = true }) { Text("Server") }
                },
            )
        },
    )

    if (showSettings) {
        ServerSettingsDialog(
            current = models.baseUrl,
            onSave = { url ->
                viewModel.updateBaseUrl(url)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun ServerSettingsDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server address") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Base URL") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(url) },
                enabled = AppViewModel.normalizeBaseUrl(url) != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
