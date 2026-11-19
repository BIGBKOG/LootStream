package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextMuted

@Composable
fun SettingsFooter(
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_lootstream_logo),
            contentDescription = "LootStream Logo",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "LootStream V${BuildConfig.VERSION_NAME} • ${s.aboutSubtitle}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = s.aboutFooterCredit,
            fontSize = 11.sp,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(NeonGreen.copy(alpha = 0.15f))
                .border(0.5.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = when (lang) {
                    AppLanguage.SPANISH -> "✓ Privado y Local • Cero Rastreo • Media3"
                    AppLanguage.FRENCH -> "✓ Privé & Local • Zéro Traqueur • Media3"
                    AppLanguage.ENGLISH -> "✓ Private & Local-First • Zero Tracking • Media3"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = NeonGreen
            )
        }
    }
}
