package com.ridesync.app.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesync.app.license.LicenseState
import com.ridesync.app.license.Tier
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.SecondaryButton
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * Subscription / license screen. Shows the current plan and rider limit, lets
 * the host paste a license key from the website to unlock more riders, and
 * links out to the website to buy or manage a plan.
 */
@Composable
fun SubscriptionScreen(
    license: LicenseState,
    onBack: () -> Unit,
    onActivate: (String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onManageOnline: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    var keyInput by remember { mutableStateOf("") }

    RideScaffold(title = "Subscription", onBack = onBack) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))

            CurrentPlanCard(license, onRefresh)

            Spacer(Modifier.height(Space.l))
            SectionLabel("Have a license key?")
            Spacer(Modifier.height(Space.s))
            RideCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Buy a plan on the website, then paste your key here to unlock it on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
                Spacer(Modifier.height(Space.m))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it.uppercase().take(24) },
                    singleLine = true,
                    label = { Text("License key") },
                    placeholder = { Text("RSYNC-XXXX-XXXX-XXXX") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    isError = license.lastError != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (license.lastError != null) {
                    Spacer(Modifier.height(Space.xs))
                    Text(license.lastError, style = MaterialTheme.typography.bodySmall, color = colors.statusRed)
                }
                Spacer(Modifier.height(Space.m))
                PrimaryButton(
                    text = if (license.activating) "Activating…" else "Activate",
                    onClick = { if (!license.activating) onActivate(keyInput) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Space.l))
            SectionLabel("Plans")
            Spacer(Modifier.height(Space.s))
            Tier.ordered.forEach { tier ->
                PlanRow(tier = tier, current = tier == license.effectiveTier)
                Spacer(Modifier.height(Space.s))
            }

            Spacer(Modifier.height(Space.s))
            PrimaryButton(
                text = "Buy or manage a plan online",
                onClick = onManageOnline,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "Only the host needs a plan — it sets the rider limit for the whole ride.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.faintText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m),
            )

            if (license.hasKey) {
                Spacer(Modifier.height(Space.l))
                SecondaryButton(
                    text = "Remove key from this phone",
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun CurrentPlanCard(license: LicenseState, onRefresh: () -> Unit) {
    val colors = RideSyncTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.accent.copy(alpha = 0.10f))
            .border(androidx.compose.foundation.BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)), shape)
            .padding(Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Current plan", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                Text(
                    license.effectiveTier.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            StatusPill(license)
        }
        Spacer(Modifier.height(Space.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Up to ${license.maxRiders} riders",
                style = MaterialTheme.typography.titleMedium,
                color = colors.accentBright,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val sub = planSubtitle(license)
        if (sub != null) {
            Spacer(Modifier.height(Space.xs))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
        }
        if (license.hasKey) {
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Key ${license.key}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.faintText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            if (license.lastValidationFailed) {
                Text(
                    "Couldn't reach the server just now — showing your saved plan. It'll refresh when you're back online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.statusYellow,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(license: LicenseState) {
    val colors = RideSyncTheme.colors
    val (label, color) = when (license.effectiveStatus) {
        LicenseState.Status.ACTIVE -> "Active" to colors.statusGreen
        LicenseState.Status.EXPIRED -> "Expired" to colors.statusRed
        else -> "Free" to colors.accent
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = Space.m, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlanRow(tier: Tier, current: Boolean) {
    val colors = RideSyncTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (current) colors.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(
                androidx.compose.foundation.BorderStroke(1.dp, if (current) colors.accent else colors.cardStroke),
                shape,
            )
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tier.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (current) {
                    Spacer(Modifier.width(Space.s))
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Current", tint = colors.statusGreen, modifier = Modifier.size(16.dp))
                }
            }
            Text("Up to ${tier.maxUsers} riders", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
        }
        Text(
            if (tier.priceInr == 0) "Free" else "₹${tier.priceInr}/mo",
            style = MaterialTheme.typography.titleMedium,
            color = if (tier.priceInr == 0) colors.mutedText else colors.accentBright,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun planSubtitle(license: LicenseState): String? {
    val when0 = license.expiresAtMs?.let { formatDate(it) }
    return when (license.effectiveStatus) {
        LicenseState.Status.ACTIVE -> if (when0 != null) "Renews / expires on $when0." else null
        LicenseState.Status.EXPIRED -> "Your plan lapsed${if (when0 != null) " on $when0" else ""} — you're on the Free limit until you renew."
        else -> "Free forever. Upgrade anytime to ride with more people."
    }
}

private fun formatDate(ms: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
}.getOrDefault("")
