package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.SakhaEngine
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOutUser
import com.example.data.NexoraRepository
import com.example.firebase.configureAppCheck
import com.example.model.AppLanguage
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainScaffold
import com.example.ui.theme.Crimson50
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Navy900
import com.example.ui.theme.Navy950
import com.example.ui.theme.NexoraTheme
import com.example.ui.viewmodel.NexoraViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            NexoraTheme {
                NexoraApp()
            }
        }
    }
}

@Composable
private fun NexoraApp() {
    val context = LocalContext.current
    val isFirebaseReady = remember(context) {
        runCatching {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        }.getOrDefault(false)
    }

    if (!isFirebaseReady) {
        FirebaseConfigMissingScreen()
        return
    }

    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var authLanguage by remember { mutableStateOf(AppLanguage.EN) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            language = authLanguage,
            onLanguageChange = { authLanguage = it },
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            val vm: NexoraViewModel = viewModel(
                key = uid,
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return NexoraViewModel(
                            repository = NexoraRepository(context.applicationContext),
                            sakhaEngine = SakhaEngine(),
                            currentUserId = uid,
                            userEmail = user.email.orEmpty(),
                            userDisplayName = user.displayName.orEmpty()
                        ) as T
                    }
                }
            )
            MainScaffold(
                viewModel = vm,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
private fun FirebaseConfigMissingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy950, Navy900)))
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("firebase_missing_card"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Firebase configuration missing",
                    tint = Crimson600,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Firebase Config Missing in APK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Surface(
                    color = Crimson50,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Is APK build mein app/google-services.json shamil nahi tha, isliye Firebase Auth/Database start nahi ho paaya.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Crimson600,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = "Fix: AI Studio ke Settings menu se seedhe APK export/generate karein, ya GitHub repo ke app/ folder mein google-services.json add karke dubara build karein.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
