package com.shakti.ai.stealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shakti.ai.stealth.StealthBodyguardManager
import kotlinx.coroutines.launch

/**
 * Hidden Calculator Screen
 *
 * Appears as a normal calculator but secretly runs:
 * - Scream detection
 * - Voice trigger detection ("HELP" 3x)
 * - Evidence recording
 * - Blockchain anchoring
 *
 * The user sees a fully functional calculator.
 * In the background, stealth bodyguard monitors for threats.
 */
@Composable
fun HiddenCalculatorScreen() {
    val context = LocalContext.current
    val bodyguardManager = remember { StealthBodyguardManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Calculator state
    var display by remember { mutableStateOf("0") }
    var currentOperation by remember { mutableStateOf<String?>(null) }
    var operand by remember { mutableStateOf<Double?>(null) }
    var shouldClearDisplay by remember { mutableStateOf(false) }

    // Stealth state
    val stealthState by bodyguardManager.stealthState.collectAsState()
    val detectionResult by bodyguardManager.detectionResult.collectAsState()

    // Start monitoring when screen loads
    LaunchedEffect(Unit) {
        bodyguardManager.startMonitoring()
    }

    // Dispose when screen closes
    DisposableEffect(Unit) {
        onDispose {
            bodyguardManager.stopMonitoring()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculator") },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = Color.White
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1E1E1E))
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = display,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )

                    // Stealth monitoring indicator
                    if (stealthState.isMonitoring) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (stealthState.isEmergency) Color.Red else Color.Green,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (stealthState.isEmergency) "Recording" else "Monitoring",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    // Detection indicator (for demo purposes)
                    detectionResult?.let { result ->
                        if (result.screamConfidence > 0.5f || result.voiceTriggerConfidence > 0.5f) {
                            Text(
                                text = "Detection: ${
                                    (maxOf(
                                        result.screamConfidence,
                                        result.voiceTriggerConfidence
                                    ) * 100).toInt()
                                }%",
                                fontSize = 8.sp,
                                color = if (result.isScream || result.isVoiceTrigger) Color.Red else Color.Yellow,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Help counter indicator
                    if (stealthState.helpCount > 0) {
                        Text(
                            text = "HELP ${stealthState.helpCount}/${stealthState.helpCountThreshold}",
                            fontSize = 10.sp,
                            color = if (stealthState.helpCount >= 2) Color.Red else Color.Yellow,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calculator buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: C, ⌫, %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton("C", Color(0xFFA5A5A5), modifier = Modifier.weight(1f)) {
                        display = "0"
                        currentOperation = null
                        operand = null
                        shouldClearDisplay = false
                    }
                    CalculatorButton("⌫", Color(0xFFA5A5A5), modifier = Modifier.weight(1f)) {
                        display = if (display.length > 1) {
                            display.dropLast(1)
                        } else "0"
                    }
                    CalculatorButton("%", Color(0xFFA5A5A5), modifier = Modifier.weight(1f)) {
                        handleOperation(
                            "%",
                            display,
                            operand,
                            currentOperation
                        ) { newDisplay, newOperand, newOperation ->
                            display = newDisplay
                            operand = newOperand
                            currentOperation = newOperation
                            shouldClearDisplay = true
                        }
                    }
                    CalculatorButton("÷", Color(0xFFFF9500), modifier = Modifier.weight(1f)) {
                        handleOperation(
                            "÷",
                            display,
                            operand,
                            currentOperation
                        ) { newDisplay, newOperand, newOperation ->
                            display = newDisplay
                            operand = newOperand
                            currentOperation = newOperation
                            shouldClearDisplay = true
                        }
                    }
                }

                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton("7", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "7", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("8", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "8", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("9", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "9", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("×", Color(0xFFFF9500), modifier = Modifier.weight(1f)) {
                        handleOperation(
                            "×",
                            display,
                            operand,
                            currentOperation
                        ) { newDisplay, newOperand, newOperation ->
                            display = newDisplay
                            operand = newOperand
                            currentOperation = newOperation
                            shouldClearDisplay = true
                        }
                    }
                }

                // Row 3: 4, 5, 6, −
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton("4", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "4", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("5", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "5", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("6", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "6", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("−", Color(0xFFFF9500), modifier = Modifier.weight(1f)) {
                        handleOperation(
                            "−",
                            display,
                            operand,
                            currentOperation
                        ) { newDisplay, newOperand, newOperation ->
                            display = newDisplay
                            operand = newOperand
                            currentOperation = newOperation
                            shouldClearDisplay = true
                        }
                    }
                }

                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton("1", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "1", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("2", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "2", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("3", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "3", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton("+", Color(0xFFFF9500), modifier = Modifier.weight(1f)) {
                        handleOperation(
                            "+",
                            display,
                            operand,
                            currentOperation
                        ) { newDisplay, newOperand, newOperation ->
                            display = newDisplay
                            operand = newOperand
                            currentOperation = newOperation
                            shouldClearDisplay = true
                        }
                    }
                }

                // Row 5: ±, 0, ., =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalculatorButton("±", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        val value = display.toDoubleOrNull() ?: 0.0
                        display = (-value).toString()
                    }
                    CalculatorButton("0", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        display = appendDigit(display, "0", shouldClearDisplay)
                        shouldClearDisplay = false
                    }
                    CalculatorButton(".", Color(0xFF505050), modifier = Modifier.weight(1f)) {
                        if (!display.contains(".")) {
                            display += "."
                        }
                    }
                    CalculatorButton("=", Color(0xFFFF9500), modifier = Modifier.weight(1f)) {
                        val result = calculateResult(display, operand, currentOperation)
                        display = result.toString()
                        currentOperation = null
                        operand = null
                        shouldClearDisplay = true
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emergency status (if active)
            if (stealthState.isEmergency) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color.Red.copy(alpha = 0.2f),
                    elevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🚨 EMERGENCY ACTIVE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                        Text(
                            text = "Evidence ID: ${stealthState.evidenceId}",
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (stealthState.evidenceHash != null) {
                            Text(
                                text = "Hash: ${stealthState.evidenceHash?.take(16)}...",
                                fontSize = 8.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    bodyguardManager.stopRecording()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                        ) {
                            Text("Stop Recording", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorButton(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(70.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

// Calculator logic helpers
private fun appendDigit(current: String, digit: String, shouldClear: Boolean): String {
    return if (shouldClear || current == "0") {
        digit
    } else {
        current + digit
    }
}

private fun handleOperation(
    operation: String,
    display: String,
    operand: Double?,
    currentOperation: String?,
    onUpdate: (String, Double?, String) -> Unit
) {
    val currentValue = display.toDoubleOrNull() ?: 0.0

    if (operand != null && currentOperation != null) {
        val result = calculateResult(display, operand, currentOperation)
        onUpdate(result.toString(), result, operation)
    } else {
        onUpdate(display, currentValue, operation)
    }
}

private fun calculateResult(display: String, operand: Double?, operation: String?): Double {
    val currentValue = display.toDoubleOrNull() ?: 0.0

    return when (operation) {
        "+" -> (operand ?: 0.0) + currentValue
        "−" -> (operand ?: 0.0) - currentValue
        "×" -> (operand ?: 1.0) * currentValue
        "÷" -> {
            if (currentValue != 0.0) {
                (operand ?: 0.0) / currentValue
            } else {
                0.0
            }
        }

        "%" -> (operand ?: 0.0) * (currentValue / 100.0)
        else -> currentValue
    }
}
