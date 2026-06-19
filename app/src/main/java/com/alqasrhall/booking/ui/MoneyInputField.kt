@file:Suppress("MagicNumber")

package com.alqasrhall.booking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alqasrhall.booking.ui.theme.GoldenClassic
import com.alqasrhall.booking.ui.theme.DeepCharcoal
import com.alqasrhall.booking.ui.theme.RedCancel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MoneyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: String, // "Yemeni Rial", "Saudi Riyal", "USD"
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    testTag: String? = null
) {
    val cleanText = value.replace(",", "").replace(" ", "")
    val formattedText = remember(cleanText) {
        if (cleanText.isEmpty()) ""
        else {
            val num = cleanText.toLongOrNull()
            if (num != null) {
                NumberFormat.getIntegerInstance(Locale.US).format(num)
            } else {
                cleanText
            }
        }
    }

    val isValid = cleanText.isEmpty() || cleanText.toLongOrNull() != null
    
    val wordsText = remember(cleanText, currency) {
        if (cleanText.isEmpty()) ""
        else {
            val num = cleanText.toLongOrNull()
            if (num != null && num >= 0) {
                if (currency == "USD") {
                    NumberToWords.convertToEnglish(num, currency)
                } else {
                    NumberToWords.convertToArabic(num, currency)
                }
            } else {
                ""
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        OutlinedTextField(
            value = formattedText,
            onValueChange = { newValue ->
                val cleanNewValue = newValue.replace(",", "").replace(" ", "")
                if (cleanNewValue.all { it.isDigit() }) {
                    onValueChange(cleanNewValue)
                }
            },
            label = { Text(label, textAlign = TextAlign.Right) },
            placeholder = placeholder?.let { { Text(it, textAlign = TextAlign.Right) } },
            isError = isError || !isValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .let { if (testTag != null) it.testTag(testTag) else it },
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                textDirection = TextDirection.Rtl,
                textAlign = TextAlign.Right
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldenClassic,
                unfocusedBorderColor = Color.DarkGray,
                focusedContainerColor = DeepCharcoal,
                unfocusedContainerColor = DeepCharcoal
            ),
            singleLine = true
        )

        val showErr = !isValid || (isError && errorMessage != null)
        val shownErrorMsg = if (!isValid) "يرجى إدخال مبلغ صحيح وموجب" else errorMessage

        if (showErr && shownErrorMsg != null) {
            Text(
                text = shownErrorMsg,
                color = RedCancel,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, end = 4.dp)
            )
        }

        AnimatedVisibility(
            visible = wordsText.isNotEmpty() && isValid,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.padding(top = 4.dp, end = 4.dp)
        ) {
            Text(
                text = wordsText,
                color = GoldenClassic.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
