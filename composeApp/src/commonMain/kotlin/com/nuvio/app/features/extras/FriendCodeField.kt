package com.nuvio.app.features.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val GROUP_SIZE = 4

/**
 * Friend code entry as three fields of four characters (XXXX-XXXX-XXXX). Typing or pasting
 * is upper-cased and stripped to the code alphabet, so dashes and spaces just vanish.
 * [code] holds only the normalized characters.
 */
@Composable
fun FriendCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    onDone: () -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        // The caret always sits at the end: boxes fill left to right.
        value = TextFieldValue(code, selection = TextRange(code.length)),
        onValueChange = { onCodeChange(normalizeFriendCode(it.text).take(FRIEND_CODE_LENGTH)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        decorationBox = {
            // Three groups of four, matching how the code is shown (XXXX-XXXX-XXXX).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(FRIEND_CODE_LENGTH / GROUP_SIZE) { group ->
                    val start = group * GROUP_SIZE
                    val activeIndex = code.length.coerceAtMost(FRIEND_CODE_LENGTH - 1)
                    val groupActive = activeIndex in start until start + GROUP_SIZE
                    val borderColor = when {
                        isError -> colors.error
                        groupActive -> colors.primary
                        else -> Color.Transparent
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceContainerHighest)
                            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp)),
                    ) {
                        repeat(GROUP_SIZE) { offset ->
                            val index = start + offset
                            val char = code.getOrNull(index)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = char?.toString() ?: "•",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = if (char != null) 20.sp else 14.sp,
                                    color = if (char != null) colors.onSurface else colors.outline,
                                )
                                // Caret: a short bar under the next position to fill.
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(width = 12.dp, height = 2.dp)
                                        .background(
                                            if (index == activeIndex && code.length < FRIEND_CODE_LENGTH) colors.primary else Color.Transparent,
                                            RoundedCornerShape(1.dp),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
