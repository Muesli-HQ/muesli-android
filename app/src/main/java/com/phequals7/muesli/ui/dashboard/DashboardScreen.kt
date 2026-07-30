package com.phequals7.muesli.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.data.entity.CustomWord
import com.phequals7.muesli.model.ModelManager
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme
import com.phequals7.muesli.ui.dashboard.meetings.MeetingsTab
import com.phequals7.muesli.ui.dashboard.settings.SettingsTab
import com.phequals7.muesli.ui.dashboard.voicenotes.VoiceNotesTab
import kotlinx.coroutines.launch

enum class DashboardTab(val label: String, val icon: ImageVector) {
    DICTATIONS("Voice Notes", Icons.Default.GraphicEq),
    MEETINGS("Meetings", Icons.Default.Groups),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { SharedStore(context) }
    val colors = MuesliTheme.colors

    var activeTab by remember { mutableStateOf(DashboardTab.DICTATIONS) }

    Scaffold(
        bottomBar = {
            MuesliBottomNav(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )
        },
        containerColor = colors.backgroundDeep,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                DashboardTab.DICTATIONS -> VoiceNotesTab(
                    store = store,
                    onNavigateToSettings = { activeTab = DashboardTab.SETTINGS }
                )
                DashboardTab.MEETINGS -> MeetingsTab(
                    store = store,
                    onNavigateToSettings = { activeTab = DashboardTab.SETTINGS }
                )
                DashboardTab.SETTINGS -> SettingsTab(store)
            }
        }
    }
}

/**
 * Floating glass pill navigation, modeled on the muesli-ios liquid-glass nav
 * (MuesliTheme.Navigation: 24dp container corner, 23dp selection corner,
 * 28dp horizontal inset, 44dp item height, accent-tinted selection).
 */
@Composable
private fun MuesliBottomNav(
    activeTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    val colors = MuesliTheme.colors
    val containerShape = RoundedCornerShape(MuesliCorners.navContainer)
    val selectionShape = RoundedCornerShape(MuesliCorners.navSelection)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = MuesliSpacing.s12)
            .clip(containerShape)
            .background(colors.backgroundRaised)
            .border(1.dp, colors.surfaceBorder, containerShape)
            .padding(MuesliSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s4)
    ) {
        DashboardTab.entries.forEach { tab ->
            val selected = tab == activeTab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(selectionShape)
                    .background(if (selected) colors.accent.copy(alpha = 0.24f) else Color.Transparent)
                    .then(
                        if (selected) {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.20f), selectionShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onTabSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tab.label,
                    color = if (selected) colors.accent else colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ======================== TABS CONTENT IMPLEMENTATIONS ========================
