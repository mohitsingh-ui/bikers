package com.ridesync.app.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme
import kotlinx.coroutines.launch

private data class Page(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val extra: String? = null,
)

/**
 * Four-slide onboarding. Intentionally short: swipe or tap Continue. Last slide
 * is Get Started. Includes the privacy/local-connection statement.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val colors = RideSyncTheme.colors
    val pages = listOf(
        Page("RIDE TOGETHER", "Stay connected with your crew while you ride.", Icons.Filled.Wifi),
        Page("TALK", "Push-to-talk voice communication with your riding group.", Icons.Filled.Mic),
        Page("LISTEN TOGETHER", "Synchronize music across your entire riding group.", Icons.Filled.MusicNote),
        Page(
            "LOCAL CONNECTION",
            "Connect through one phone’s Wi-Fi hotspot.",
            Icons.Filled.Wifi,
            extra = "No traditional phone call required.",
        ),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Space.xl),
    ) {
        Spacer(Modifier.height(Space.l))
        Row(
            Modifier.fillMaxWidth().padding(top = Space.l),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RideSync", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Skip",
                color = colors.mutedText,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .then(clickable(onFinished)),
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(page.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.height(Space.xxl))
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(Space.m))
                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Space.l),
                )
                if (page.extra != null) {
                    Spacer(Modifier.height(Space.s))
                    Text(
                        page.extra,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accent,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.l),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                val selected = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.accent else colors.cardStroke),
                )
            }
        }

        val isLast = pagerState.currentPage == pages.lastIndex
        PrimaryButton(
            text = if (isLast) "Get Started" else "Continue",
            onClick = {
                if (isLast) onFinished()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xl))
    }
}

private fun clickable(onClick: () -> Unit): Modifier =
    Modifier.then(androidx.compose.foundation.clickable { onClick() })
