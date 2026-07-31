package com.phequals7.muesli.ui.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phequals7.muesli.R
import com.phequals7.muesli.data.SharedStore
import com.phequals7.muesli.theme.MuesliCorners
import com.phequals7.muesli.theme.MuesliSpacing
import com.phequals7.muesli.theme.MuesliTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { SharedStore(context) }

    var profileName by remember { mutableStateOf("") }
    var micPermissionGranted by remember { mutableStateOf(false) }
    var isKeyboardEnabled by remember { mutableStateOf(false) }

    // Check permissions
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        micPermissionGranted = isGranted
    }

    // Function to check if keyboard is enabled in system settings
    fun checkKeyboardStatus() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledList = imm.enabledInputMethodList
        val myPackageName = context.packageName
        isKeyboardEnabled = enabledList.any { it.packageName == myPackageName }
    }

    LaunchedEffect(Unit) {
        checkKeyboardStatus()
        // Pre-fill mic permission state
        micPermissionGranted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Muesli brand tokens (ported from muesli-ios MuesliTheme)
    val colors = MuesliTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundDeep)
            .padding(MuesliSpacing.s24),
        verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s20),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(MuesliSpacing.s20))

        // Official Muesli brand mark (same asset as the iOS app icon)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_muesli_logo),
                contentDescription = "Muesli logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(MuesliCorners.large))
            )
            Spacer(modifier = Modifier.height(MuesliSpacing.s12))
            Text(
                text = "muesli",
                color = colors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp
            )
            Text(
                text = "Premium voice dictation, local & private.",
                color = colors.textSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Onboarding checklist / features card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MuesliCorners.medium))
                .background(colors.backgroundRaised)
                .border(1.dp, colors.surfaceBorder, RoundedCornerShape(MuesliCorners.medium))
                .padding(MuesliSpacing.s16),
            verticalArrangement = Arrangement.spacedBy(MuesliSpacing.s16)
        ) {
            Text(
                text = "GET STARTED",
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Step 1: Profile Name
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your Name", color = colors.textPrimary, fontSize = 14.sp)
                TextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    placeholder = { Text("e.g. Pranav", color = colors.textTertiary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfacePrimary,
                        unfocusedContainerColor = colors.surfacePrimary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    shape = RoundedCornerShape(MuesliCorners.medium),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = colors.surfaceBorder)

            // Step 2: Mic Permission Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(MuesliCorners.small))
                        .background(if (micPermissionGranted) colors.syncGreenSubtle else colors.surfacePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (micPermissionGranted) Icons.Default.Check else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (micPermissionGranted) colors.syncGreen else colors.textPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Microphone Access", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Required for dictation", color = colors.textSecondary, fontSize = 11.sp)
                }
                if (!micPermissionGranted) {
                    Button(
                        onClick = { micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Grant", fontSize = 12.sp)
                    }
                }
            }

            HorizontalDivider(color = colors.surfaceBorder)

            // Step 3: Keyboard IME Enable Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MuesliSpacing.s12),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(MuesliCorners.small))
                        .background(if (isKeyboardEnabled) colors.syncGreenSubtle else colors.surfacePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isKeyboardEnabled) Icons.Default.Check else Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = if (isKeyboardEnabled) colors.syncGreen else colors.textPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Keyboard", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Settings -> Keyboards -> Muesli", color = colors.textSecondary, fontSize = 11.sp)
                }
                if (!isKeyboardEnabled) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(MuesliCorners.medium),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Setup", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Complete Onboarding Button
        Button(
            onClick = {
                if (profileName.isNotBlank() && micPermissionGranted) {
                    store.userProfileName = profileName
                    store.isOnboardingCompleted = true
                    onFinished()
                }
            },
            enabled = profileName.isNotBlank() && micPermissionGranted,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                disabledContainerColor = colors.accent.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(MuesliCorners.medium),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Complete Setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
