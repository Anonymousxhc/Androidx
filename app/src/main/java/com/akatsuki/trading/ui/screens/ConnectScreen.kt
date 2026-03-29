package com.akatsuki.trading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.data.model.AuthState
import com.akatsuki.trading.ui.components.GradientButton
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AuthViewModel

@Composable
fun ConnectScreen(
    authViewModel: AuthViewModel,
    onConnected: () -> Unit,
    onEditCredentials: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    var totp by remember { mutableStateOf("") }

    LaunchedEffect(authState) {
        if (authState is AuthState.Connected) onConnected()
    }

    val isLoading = authState is AuthState.Loading
    val error = (authState as? AuthState.Error)?.message

    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border2)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("⚡", fontSize = 40.sp)

                Text(
                    text = "AKATSUKI",
                    fontFamily = FontFamily.Monospace,
                    color = Blue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Enter TOTP",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "6-digit code from your authenticator app",
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = totp,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            totp = it
                            if (it.length == 6 && !isLoading) {
                                authViewModel.clearError()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "000000",
                            color = TextMuted,
                            fontSize = 24.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        letterSpacing = 8.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue,
                        unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Blue,
                        focusedContainerColor = BackgroundAlt,
                        unfocusedContainerColor = BackgroundAlt
                    ),
                    shape = RoundedCornerShape(8.dp),
                    isError = error != null
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = Red,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = Blue,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    GradientButton(
                        text = "Connect",
                        onClick = { authViewModel.submitTotp(totp) },
                        modifier = Modifier.fillMaxWidth(),
                        gradient = Brush.linearGradient(listOf(Green, GreenDark)),
                        enabled = totp.length == 6
                    )
                }

                TextButton(onClick = onEditCredentials) {
                    Text(
                        text = "Edit API Credentials",
                        color = Blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
