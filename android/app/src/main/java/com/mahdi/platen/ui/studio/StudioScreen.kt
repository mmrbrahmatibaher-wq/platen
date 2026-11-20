package com.mahdi.platen.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahdi.platen.domain.NoteLimits
import com.mahdi.platen.ui.theme.LocalPalette

/**
 * Placeholder for the one-time "Studio" purchase. There is no Play Billing here: the button is a
 * clearly labelled local demo switch so every feature can be tried.
 */
@Composable
fun StudioScreen(unlocked: Boolean, onToggle: (Boolean) -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.background).statusBarsPadding().navigationBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp).testTag("back")) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = p.onBackground)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 26.dp)) {
            Text("platen", style = MaterialTheme.typography.headlineMedium, color = p.muted)
            Text("Studio", style = MaterialTheme.typography.headlineLarge, color = p.onBackground)
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Feature("More sheets on the desk", "Free keeps ${NoteLimits.FREE_ACTIVE_NOTES} active notes. Studio removes the cap.")
                Feature("Print-ready letters", "PDF export in A4, US Letter and A5 with a typed-letter layout.")
                Feature("Same honest page", "Every note still stops at 3000 characters. That's the point.")
            }
            Spacer(Modifier.height(24.dp))
            if (unlocked) {
                Text("Studio is unlocked on this device (demo).", style = MaterialTheme.typography.titleSmall, color = p.onBackground, modifier = Modifier.testTag("studioState"))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { onToggle(false) }, modifier = Modifier.fillMaxWidth().testTag("studioToggle")) { Text("Turn off demo unlock") }
            } else {
                Button(
                    onClick = { onToggle(true) },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("studioToggle"),
                    colors = ButtonDefaults.buttonColors(containerColor = p.roller, contentColor = p.capsule),
                ) { Text("Unlock Studio · demo") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "In-app purchase isn't connected in this build, so unlocking is free and stays on this device.",
                style = MaterialTheme.typography.bodySmall, color = p.muted,
            )
        }
    }
}

@Composable
private fun Feature(title: String, body: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Check, null, tint = p.led, modifier = Modifier.padding(top = 2.dp, end = 10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = p.onBackground)
            Text(body, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
    }
}
