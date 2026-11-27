package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.SakhaParseResult
import com.example.i18n.stringsFor
import com.example.model.AppLanguage
import com.example.model.Customer
import com.example.model.SakhaChatMessage
import com.example.model.SakhaPendingAction
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.Crimson50
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.Navy900
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.util.formatINR
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SakhaSheet(
    language: AppLanguage,
    messages: List<SakhaChatMessage>,
    pendingAction: SakhaPendingAction?,
    disambiguation: SakhaParseResult.DisambiguateCustomer?,
    isBusy: Boolean,
    speakEvents: Flow<String>,
    onSendMessage: (String) -> Unit,
    onPickCustomer: (Customer) -> Unit,
    onConfirmPending: () -> Unit,
    onCancelPending: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = stringsFor(language)
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var voiceReplyEnabled by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(false) }
    var voiceNotice by remember { mutableStateOf<String?>(null) }

    // Text-to-Speech engine for Sakha replies
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
        engine
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
    LaunchedEffect(language, ttsReady) {
        if (ttsReady) {
            tts.language = Locale.forLanguageTag(language.speechLocale)
        }
    }
    LaunchedEffect(speakEvents, voiceReplyEnabled, ttsReady) {
        speakEvents.collect { msg ->
            if (voiceReplyEnabled && ttsReady && msg.isNotBlank()) {
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "sakha_${System.currentTimeMillis()}")
            }
        }
    }

    // Speech-to-Text recognizer
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; voiceNotice = null }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                voiceNotice = "Mic could not hear clearly. Please type or tap mic again."
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    onSendMessage(spoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) inputText = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            recognizer?.destroy()
        }
    }

    val startVoiceCapture = remember(recognizer, language) {
        {
            if (recognizer == null) {
                voiceNotice = "Voice recognition service is unavailable on this device. Please type your command."
            } else {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocale)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                voiceNotice = null
                recognizer.startListening(intent)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            voiceNotice = "Microphone permission is required for voice commands. You can still type below."
        }
    }

    LaunchedEffect(messages.size, pendingAction, disambiguation) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Navy900)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Emerald600),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(t.sakhaName, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(t.sakhaSub, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA7F3D0))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = voiceReplyEnabled,
                        onClick = {
                            voiceReplyEnabled = !voiceReplyEnabled
                            if (!voiceReplyEnabled) tts.stop()
                        },
                        label = {
                            Text(if (voiceReplyEnabled) "Voice ON" else "Muted", style = MaterialTheme.typography.labelSmall)
                        },
                        leadingIcon = {
                            Icon(
                                if (voiceReplyEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Toggle voice response",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("sakha_voice_toggle")
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("sakha_close_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close Sakha", tint = Color.White)
                    }
                }
            }

            // Messages area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (msg.isUser) Navy900 else Slate100,
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                bottomEnd = if (msg.isUser) 4.dp else 16.dp
                            ),
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Text(
                                text = msg.text,
                                color = if (msg.isUser) Color.White else Navy900,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                if (isBusy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Emerald600)
                            Text("Sakha soch raha hai...", style = MaterialTheme.typography.labelSmall, color = Emerald600)
                        }
                    }
                }
            }

            // Customer disambiguation card
            if (disambiguation != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Amber50),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Multiple matches (${disambiguation.matches.size}) for ${formatINR(disambiguation.amount)}:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Amber600
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            disambiguation.matches.forEach { cust ->
                                Button(
                                    onClick = { onPickCustomer(cust) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("${cust.name}${if (cust.phone.isNotBlank()) " (${cust.phone})" else ""}")
                                }
                            }
                        }
                    }
                }
            }

            // Two-Step Confirmation Card ("Kardu?" -> "Kar do" / "Mat karo")
            if (pendingAction != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Emerald50),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t.sakhaAskConfirm, style = MaterialTheme.typography.titleMedium, color = Emerald600, fontWeight = FontWeight.Bold)
                        PendingActionPreview(pendingAction)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onCancelPending,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Crimson600),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sakha_cancel_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(t.cancelDo)
                            }
                            Button(
                                onClick = onConfirmPending,
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sakha_confirm_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(t.confirmDo)
                            }
                        }
                    }
                }
            }

            if (voiceNotice != null) {
                Surface(
                    color = Crimson50,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(voiceNotice!!, color = Crimson600, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                }
            }

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Aaj ki bikri kitni hai?",
                    "Aaj ka net profit?",
                    "Low stock items",
                    "Bijli bill 800 expense"
                ).forEach { chip ->
                    AssistChip(
                        onClick = { onSendMessage(chip) },
                        label = { Text(chip, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            HorizontalDivider(color = Slate200)

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = {
                        if (isListening) {
                            recognizer?.stopListening()
                            isListening = false
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startVoiceCapture()
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    shape = CircleShape,
                    color = if (isListening) Crimson600 else Emerald600,
                    modifier = Modifier.size(48.dp).testTag("sakha_mic_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Speak command",
                            tint = Color.White
                        )
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (isListening) t.listening else t.sakhaPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("sakha_text_input")
                )

                Surface(
                    onClick = {
                        val txt = inputText.trim()
                        if (txt.isNotEmpty()) {
                            inputText = ""
                            onSendMessage(txt)
                        }
                    },
                    shape = CircleShape,
                    color = Navy900,
                    modifier = Modifier.size(48.dp).testTag("sakha_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send command", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingActionPreview(action: SakhaPendingAction) {
    when (action) {
        is SakhaPendingAction.AddUdhaar -> {
            Text("Add Udhaar • ${action.customerName}", fontWeight = FontWeight.SemiBold)
            Text("Amount: ${formatINR(action.amount)} • ${action.date}", fontFamily = JetBrainsMono)
            Text(
                "Balance: ${formatINR(action.beforeBal)} → ${formatINR(action.afterBal)}",
                style = MaterialTheme.typography.labelLarge,
                color = Crimson600
            )
        }
        is SakhaPendingAction.AddUdhaarNewCustomer -> {
            Text("Create New Customer & Udhaar • ${action.customerName}", fontWeight = FontWeight.SemiBold)
            Text("Amount: ${formatINR(action.amount)} • ${action.date}", fontFamily = JetBrainsMono)
        }
        is SakhaPendingAction.ReceivePayment -> {
            Text("Receive Payment • ${action.customerName}", fontWeight = FontWeight.SemiBold)
            Text("Amount: ${formatINR(action.amount)} • ${action.date}", fontFamily = JetBrainsMono)
            Text(
                "Balance: ${formatINR(action.beforeBal)} → ${formatINR(action.afterBal)}",
                style = MaterialTheme.typography.labelLarge,
                color = Emerald600
            )
        }
        is SakhaPendingAction.AddExpense -> {
            Text("Operating Expense • ${action.category}", fontWeight = FontWeight.SemiBold)
            Text("Amount: ${formatINR(action.amount)} • ${action.note}", fontFamily = JetBrainsMono)
        }
        is SakhaPendingAction.AddPurchase -> {
            Text("Record Purchase", fontWeight = FontWeight.SemiBold)
            Text("Amount: ${formatINR(action.amount)} • ${action.note}", fontFamily = JetBrainsMono)
        }
        is SakhaPendingAction.QuickSale -> {
            Text("Quick Sale (${action.paymentMode})", fontWeight = FontWeight.SemiBold)
            Text("Amount: ${formatINR(action.amount)} • ${action.date}", fontFamily = JetBrainsMono)
        }
        is SakhaPendingAction.StockAdjust -> {
            Text("Stock In • ${action.productName}", fontWeight = FontWeight.SemiBold)
            Text(
                "Stock: ${action.beforeStock} → ${action.afterStock} ${action.unit}",
                fontFamily = JetBrainsMono,
                color = Emerald600
            )
        }
    }
}
