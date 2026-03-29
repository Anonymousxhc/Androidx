package com.akatsuki.trading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.ui.components.ConfirmDialog
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AuthViewModel

@Composable
fun SettingsScreen(authViewModel: AuthViewModel, onLogout: () -> Unit) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        ConfirmDialog(
            title = "Disconnect Session",
            message = "This will end your Kotak session. You'll need to reconnect with a new TOTP.",
            onConfirm = {
                authViewModel.logout()
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        }

        Spacer(Modifier.height(8.dp))

        SettingsSection("Account") {
            SettingsRow(
                icon = Icons.Default.Person,
                title = "Edit API Credentials",
                subtitle = "Change Kotak access token, MPIN or UCC",
                onClick = authViewModel::editCredentials
            )

            HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))

            SettingsRow(
                icon = Icons.Default.Logout,
                title = "Disconnect Session",
                subtitle = "End current Kotak session",
                onClick = { showLogoutDialog = true },
                titleColor = Red
            )
        }

        Spacer(Modifier.height(8.dp))

        SettingsSection("About") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("⚡ AKATSUKI", fontFamily = FontFamily.Monospace, color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Options Scalping Terminal", fontSize = 12.sp, color = TextMuted)
                }
                Text("v1.0.0", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted)
            }

            HorizontalDivider(color = Border, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Direct API connection · MIS · NSE/BSE · No backend", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.06.sp,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = TextPrimary
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = titleColor)
                Text(subtitle, fontSize = 11.sp, color = TextMuted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
