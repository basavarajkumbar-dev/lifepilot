@file:OptIn(ExperimentalMaterial3Api::class)

package com.lifepilot.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material date picker dialog working in LocalDate (the picker itself uses UTC millis). */
@Composable
fun LpDatePickerDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@Composable
fun LpTimePickerDialog(initialMinute: Int, is24Hour: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit, title: String = "Select time") {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Box(Modifier.fillMaxWidth().padding(top = 8.dp)) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Dialog asking for a whole number within [range] (e.g. a custom interval). */
@Composable
fun NumberInputDialog(
    title: String,
    label: String,
    initial: Int?,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    val value = text.toIntOrNull()
    val valid = value != null && value in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { s -> text = s.filter { it.isDigit() }.take(5) },
                    label = { Text(label) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Between ${range.first} and ${range.last}") },
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(value!!); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onSelect(option); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                label(option),
                                modifier = Modifier.fillMaxWidth(),
                                color = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
