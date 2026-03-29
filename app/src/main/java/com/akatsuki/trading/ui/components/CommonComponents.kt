package com.akatsuki.trading.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun StatusPill(isLive: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (isLive) GreenAlpha else RedAlpha
    val textColor = if (isLive) Green else Red
    val label = if (isLive) "LIVE" else "OFFLINE"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.03.sp
        )
    }
}

@Composable
fun FlashingPriceText(
    price: Double,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    format: String = "%.2f"
) {
    var prevPrice by remember { mutableStateOf(price) }
    var flashColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(price) {
        if (prevPrice != price && prevPrice != 0.0) {
            flashColor = if (price > prevPrice) Green else Red
            delay(200L)
            flashColor = null
        }
        prevPrice = price
    }

    val textColor by animateColorAsState(
        targetValue = flashColor ?: color,
        animationSpec = tween(200),
        label = "price_flash"
    )

    Text(
        text = if (price > 0) format.format(price) else "—",
        color = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.linearGradient(listOf(Blue, BlueDark)),
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled) gradient else Brush.linearGradient(listOf(Border, Border)),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (enabled) Color.White else TextMuted
            )
        }
    }
}

@Composable
fun TradeButton(
    text: String,
    onClick: () -> Unit,
    isGreen: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val gradient = if (isGreen)
        Brush.linearGradient(listOf(Green, GreenDark))
    else
        Brush.linearGradient(listOf(Red, RedDark))

    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled) gradient else Brush.linearGradient(listOf(Border, Border)),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.05.sp,
                color = if (enabled) Color.White else TextMuted
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.sp,
        color = TextMuted,
        modifier = modifier
    )
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

@Composable
fun PnlText(pnl: Double, modifier: Modifier = Modifier, fontSize: androidx.compose.ui.unit.TextUnit = 14.sp) {
    val color = when {
        pnl > 0 -> Green
        pnl < 0 -> Red
        else -> TextSecondary
    }
    val prefix = when {
        pnl > 0 -> "+"
        else -> ""
    }
    Text(
        text = "${prefix}₹${"%.2f".format(pnl)}",
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

@Composable
fun LotSelector(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SectionLabel("LOTS")
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onDecrement,
            modifier = Modifier.size(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Surface2, RoundedCornerShape(5.dp))
                    .border(1.dp, Border, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("−", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .background(Background, RoundedCornerShape(5.dp))
                .border(1.dp, Border, RoundedCornerShape(5.dp))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.toString(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
        IconButton(
            onClick = onIncrement,
            modifier = Modifier.size(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Surface2, RoundedCornerShape(5.dp))
                    .border(1.dp, Border, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm", color = Red, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status.lowercase()) {
        "complete", "traded" -> Pair(GreenAlpha, Green)
        "open", "pending", "trigger pending" -> Pair(YellowAlpha, Yellow)
        "cancelled", "rejected" -> Pair(RedAlpha, Red)
        else -> Pair(BlueAlpha, Blue)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.uppercase().take(10),
            color = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp
        )
    }
}
