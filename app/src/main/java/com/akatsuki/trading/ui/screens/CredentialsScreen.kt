package com.akatsuki.trading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.ui.components.GradientButton
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AuthViewModel

@Composable
fun CredentialsScreen(
    authViewModel: AuthViewModel,
    onCredentialsSaved: () -> Unit
) {
    var accessToken by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var mpin by remember { mutableStateOf("") }
    var ucc by remember { mutableStateOf("") }
    var mpinVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border2)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text("⚡", fontSize = 40.sp, modifier = Modifier.padding(bottom = 8.dp))

                    Text(
                        text = "AKATSUKI",
                        fontFamily = FontFamily.Monospace,
                        color = Blue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Connect to Kotak NEO API",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Enter your API credentials to get started",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    CredentialField(
                        label = "ACCESS TOKEN",
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        placeholder = "Your Kotak NEO Access Token"
                    )

                    CredentialField(
                        label = "MOBILE NUMBER",
                        value = mobileNumber,
                        onValueChange = { mobileNumber = it },
                        placeholder = "Registered mobile number",
                        keyboardType = KeyboardType.Phone
                    )

                    CredentialField(
                        label = "MPIN",
                        value = mpin,
                        onValueChange = { mpin = it },
                        placeholder = "Your MPIN",
                        isPassword = true,
                        passwordVisible = mpinVisible,
                        onPasswordToggle = { mpinVisible = !mpinVisible },
                        keyboardType = KeyboardType.NumberPassword
                    )

                    CredentialField(
                        label = "UCC",
                        value = ucc,
                        onValueChange = { ucc = it },
                        placeholder = "Client code / UCC"
                    )

                    Spacer(Modifier.height(8.dp))

                    GradientButton(
                        text = "Save & Continue",
                        onClick = {
                            authViewModel.saveCredentials(accessToken, mobileNumber, mpin, ucc)
                            onCredentialsSaved()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accessToken.isNotBlank() && mobileNumber.isNotBlank() &&
                                mpin.isNotBlank() && ucc.isNotBlank()
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.06.sp,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextMuted, fontSize = 13.sp) },
            singleLine = !label.contains("TOKEN"),
            maxLines = if (label.contains("TOKEN")) 3 else 1,
            visualTransformation = if (isPassword && !passwordVisible)
                PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword && onPasswordToggle != null) {
                {
                    IconButton(onClick = onPasswordToggle) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Blue,
                unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Blue,
                focusedContainerColor = BackgroundAlt,
                unfocusedContainerColor = BackgroundAlt
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}
