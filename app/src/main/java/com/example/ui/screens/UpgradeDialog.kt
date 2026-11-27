package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.i18n.stringsFor
import com.example.model.AppLanguage
import com.example.model.SubscriptionInfo
import com.example.model.SubscriptionPlan
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.Crimson50
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.Navy900
import com.example.ui.theme.Slate200
import com.example.util.formatINR

@Composable
fun UpgradeDialog(
    language: AppLanguage,
    plans: List<SubscriptionPlan>,
    subInfo: SubscriptionInfo,
    isSuperAdmin: Boolean,
    onAdminActivatePlan: (planId: String, billingDays: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val t = stringsFor(language)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 580.dp)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.upgradeTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (subInfo.expired) t.trialExpired else "${subInfo.plan.name} • ${subInfo.daysLeft}d left",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (subInfo.expired) Crimson600 else Emerald600
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("upgrade_close_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = t.upgradeSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = if (subInfo.expired) Crimson50 else Amber50,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = if (subInfo.expired) Crimson600 else Amber600)
                        Text(
                            text = t.gatewayNotice,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (subInfo.expired) Crimson600 else Amber600
                        )
                    }
                }

                plans.sortedBy { it.sortOrder }.filter { it.code != "trial" }.forEach { plan ->
                    val isCurrent = !subInfo.expired && subInfo.activePlanCode == plan.code
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isCurrent) Emerald50 else Color.White),
                        border = BorderStroke(1.dp, if (isCurrent) Emerald600 else Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (plan.badge.isNotBlank()) {
                                        Text(plan.badge, style = MaterialTheme.typography.labelSmall, color = Emerald600)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${formatINR(plan.price)} / ${plan.billingDays}d",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        color = Navy900
                                    )
                                }
                            }

                            plan.highlights.forEach { hl ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald600, modifier = Modifier.padding(top = 1.dp))
                                    Text(hl, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            if (isSuperAdmin) {
                                Spacer(Modifier.padding(top = 2.dp))
                                Button(
                                    onClick = { onAdminActivatePlan(plan.code, plan.billingDays) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("upgrade_admin_activate_${plan.code}")
                                ) {
                                    Text("Super Admin: Activate ${plan.name}")
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("upgrade_dismiss_button")
                ) {
                    Text(t.cancel)
                }
            }
        }
    }
}
