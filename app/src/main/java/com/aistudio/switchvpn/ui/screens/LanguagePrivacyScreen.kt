package com.aistudio.switchvpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.switchvpn.data.localization.AppStrings
import com.aistudio.switchvpn.ui.theme.AccentCyan
import com.aistudio.switchvpn.ui.theme.BgCard
import com.aistudio.switchvpn.ui.theme.BgCardElevated
import com.aistudio.switchvpn.ui.theme.BgMidnight
import com.aistudio.switchvpn.ui.theme.PrimaryBlue
import com.aistudio.switchvpn.ui.theme.TextMuted
import com.aistudio.switchvpn.ui.theme.TextPrimary
import com.aistudio.switchvpn.ui.theme.TextSecondary

@Composable
fun LanguagePrivacyScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onAcceptAndContinue: () -> Unit
) {
    var isAccepted by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMidnight)
            .testTag("language_privacy_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(
                            Brush.linearGradient(listOf(PrimaryBlue, AccentCyan)),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = BgMidnight,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = AppStrings.get("lang_privacy_title", currentLanguage),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Language Switcher Toggle
                Row(
                    modifier = Modifier
                        .background(BgCardElevated, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val isFa = currentLanguage.equals("fa", ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFa) PrimaryBlue else BgCardElevated)
                            .clickable { onLanguageChange("fa") }
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                            .testTag("lang_btn_fa"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "فارسی",
                            color = if (isFa) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isFa) PrimaryBlue else BgCardElevated)
                            .clickable { onLanguageChange("en") }
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                            .testTag("lang_btn_en"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "English",
                            color = if (!isFa) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Privacy explanation card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard, RoundedCornerShape(18.dp))
                        .border(1.dp, BgCardElevated, RoundedCornerShape(18.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = AppStrings.get("lang_privacy_desc", currentLanguage),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Checkbox row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isAccepted = !isAccepted }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAccepted,
                        onCheckedChange = { isAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PrimaryBlue,
                            uncheckedColor = TextMuted,
                            checkmarkColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("terms_checkbox")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = AppStrings.get("accept_terms", currentLanguage),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onAcceptAndContinue,
                enabled = isAccepted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("btn_agree_continue"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    disabledContainerColor = BgCardElevated,
                    contentColor = TextPrimary,
                    disabledContentColor = TextMuted
                )
            ) {
                Text(
                    text = AppStrings.get("btn_agree_continue", currentLanguage),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
