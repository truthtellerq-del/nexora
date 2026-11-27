package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.stringsFor
import com.example.model.AppLanguage
import com.example.ui.theme.Emerald600

@Composable
fun OnboardingScreen(
    defaultOwnerName: String,
    language: AppLanguage,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onCreateShop: (
        name: String, ownerName: String, phone: String, businessType: String,
        city: String, state: String, preferredLang: AppLanguage, fyStart: String, gstin: String
    ) -> Unit
) {
    val t = stringsFor(language)
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf(defaultOwnerName) }
    var phone by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("Kirana") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var selectedLang by remember { mutableStateOf(language) }
    var fyStart by remember { mutableStateOf("April") }
    var gstin by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.onboardingTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(t.onboardingDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (canCancel) {
                        TextButton(onClick = onCancel, modifier = Modifier.testTag("onboarding_cancel_button")) {
                            Text(t.cancel)
                        }
                    }
                }

                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("${t.businessName} *") },
                    placeholder = { Text("e.g. Sharma Kirana Store") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_shop_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("${t.ownerName} *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("onboarding_owner_input")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(t.phone) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("onboarding_phone_input")
                    )
                }

                Text(t.businessType, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Kirana", "Grocery", "Wholesale", "General").forEach { type ->
                        FilterChip(
                            selected = businessType == type,
                            onClick = { businessType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("${t.city} *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("onboarding_city_input")
                    )
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text(t.state) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(t.language, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick = { selectedLang = lang },
                            label = { Text(lang.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fyStart,
                        onValueChange = { fyStart = it },
                        label = { Text(t.fyStart) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase() },
                        label = { Text(t.gstin) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        onCreateShop(
                            shopName.trim(),
                            ownerName.trim().ifEmpty { "Owner" },
                            phone.trim(),
                            businessType,
                            city.trim(),
                            state.trim(),
                            selectedLang,
                            fyStart.trim().ifEmpty { "April" },
                            gstin.trim()
                        )
                    },
                    enabled = shopName.trim().isNotEmpty() && city.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("onboarding_launch_button")
                ) {
                    Text(t.startTrialBtn, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
