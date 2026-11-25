package com.maya.assistant.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maya.assistant.ui.components.MayaOrbCanvas
import com.maya.assistant.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MayaMainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val pending by viewModel.pendingConfirmation.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val rms by viewModel.speechManager.rmsLevel.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("MAYA AI", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Hello, ${settings.preferredName}", color = MutedText, fontSize = 13.sp)
            }
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NeonCyan)
            }
        }

        if (showSettings) {
            MayaSettingsPane(viewModel = viewModel, onClose = { showSettings = false })
        } else {
            // Animated Circular AI Orb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                MayaOrbCanvas(state = uiState, rmsLevel = rms)
            }

            Text(
                text = when (uiState) {
                    AssistantUiState.IDLE -> "Tap mic or say Hey Maya"
                    AssistantUiState.WAKE_STANDBY -> "Listening for \"Hey Maya\"..."
                    AssistantUiState.LISTENING -> "Listening..."
                    AssistantUiState.THINKING -> "Thinking..."
                    AssistantUiState.SPEAKING -> "Speaking..."
                    AssistantUiState.AWAITING_CONFIRMATION -> "Waiting for confirmation"
                },
                color = NeonCyan,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(12.dp))

            // Pending Action Confirmation Card
            pending?.let { conf ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ElectricBlue, RoundedCornerShape(16.dp))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = if (conf.type == "call") "Confirm Call" else "Confirm Message",
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (conf.type == "call") "Call ${conf.recipient} (${conf.phoneNumber ?: "lookup"})?"
                            else "Send via ${conf.platform} to ${conf.recipient}: \"${conf.messageText}\"",
                            color = SlateText,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { viewModel.confirmPendingAction() }) { Text("Confirm") }
                            OutlinedButton(onClick = { viewModel.cancelPendingAction() }) { Text("Cancel") }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Chat History
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (item.isUser) Color(0xFF0F172A) else NavySurface,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (item.isUser) settings.preferredName else "MAYA",
                            color = if (item.isUser) MutedText else NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(item.text, color = SlateText, fontSize = 14.sp)
                        item.actionNote?.let { note ->
                            Text(note, color = ElectricBlue, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Voice Mic Button + Optional Text Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    placeholder = { Text("Ask in Hindi, English, Hinglish...", color = MutedText) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (textDraft.isNotBlank()) {
                    Button(onClick = {
                        viewModel.processUserUtterance(textDraft)
                        textDraft = ""
                    }) { Text("Send") }
                }
                FloatingActionButton(
                    onClick = { viewModel.toggleMic() },
                    shape = CircleShape,
                    containerColor = ElectricBlue,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = if (uiState == AssistantUiState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Voice Command"
                    )
                }
            }
        }
    }
}

@Composable
fun MayaSettingsPane(viewModel: MainViewModel, onClose: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var nameInput by remember(settings.preferredName) { mutableStateOf(settings.preferredName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MAYA Settings & Permissions", color = NeonCyan, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Preferred Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            scope.launch { viewModel.preferences.setPreferredName(nameInput) }
        }) { Text("Save Name") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Continuous Voice Conversation", color = SlateText)
            Switch(
                checked = settings.continuousMode,
                onCheckedChange = { scope.launch { viewModel.preferences.setContinuousMode(it) } }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hey Maya Wake-Word Standby", color = SlateText)
            Switch(
                checked = settings.wakeWordEnabled,
                onCheckedChange = { scope.launch { viewModel.preferences.setWakeWordEnabled(it) } }
            )
        }

        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text("Grant Notification Access") }

        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) { Text("Enable Accessibility Smart Actions") }

        Spacer(Modifier.weight(1f))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Back to MAYA") }
    }
}
