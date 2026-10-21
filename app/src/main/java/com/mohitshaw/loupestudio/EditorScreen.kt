package com.mohit.loupestudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadFromUri(uri)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Adjust, 1 = Presets

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015))
            .padding(12.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LOUPE STUDIO",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.resolutionLabel,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Open", color = Color.White, fontSize = 13.sp)
                }

                Button(
                    onClick = { viewModel.saveToGallery() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Save HD", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive GPU Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF171A21))
                .pointerInput(state.loupeEnabled) {
                    if (state.loupeEnabled) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val normX = change.position.x / size.width.toFloat().coerceAtLeast(1f)
                            val normY = change.position.y / size.height.toFloat().coerceAtLeast(1f)
                            viewModel.updateLoupePosition(normX, normY)
                        }
                    } else {
                        detectTapGestures(
                            onPress = {
                                viewModel.setShowOriginal(true)
                                tryAwaitRelease()
                                viewModel.setShowOriginal(false)
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val displayBitmap = if (state.showOriginal) state.sourceBitmap else state.previewBitmap
            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Edited Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            if (state.showOriginal) {
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "ORIGINAL",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (state.isAiRunning) {
                Surface(
                    color = Color(0xDD0E1015),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            progress = { state.aiProgress },
                            color = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${(state.aiProgress * 100).toInt()}% • ${state.statusMessage}",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status & Tool Strip (Loupe Toggle + AI Upscale 4x)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.loupeEnabled,
                onClick = { viewModel.toggleLoupe() },
                label = {
                    Text(
                        if (state.loupeEnabled) "🔍 Loupe: ON" else "🔍 Loupe: OFF",
                        color = Color.White
                    )
                }
            )

            Button(
                onClick = { viewModel.runAiEnhancer() },
                enabled = !state.isAiRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) {
                Text("✨ AI Upscale 4x", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            text = state.statusMessage,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Mode Switcher (Sliders vs Presets)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("Adjustments", color = Color.White) }
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("Style Presets", color = Color.White) }
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.updateAdjustments(Adjustments(), "Original") }) {
                Text("Reset", color = Color(0xFF38BDF8))
            }
        }

        // Controls Panel
        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AdjustmentSlider(
                    label = "Exposure",
                    value = state.adjustments.exposure,
                    range = -1.0f..1.0f,
                    onValueChange = {
                        viewModel.updateAdjustments(state.adjustments.copy(exposure = it))
                    }
                )
                AdjustmentSlider(
                    label = "Contrast",
                    value = state.adjustments.contrast,
                    range = 0.5f..2.0f,
                    onValueChange = {
                        viewModel.updateAdjustments(state.adjustments.copy(contrast = it))
                    }
                )
                AdjustmentSlider(
                    label = "Saturation",
                    value = state.adjustments.saturation,
                    range = 0.0f..2.0f,
                    onValueChange = {
                        viewModel.updateAdjustments(state.adjustments.copy(saturation = it))
                    }
                )
                AdjustmentSlider(
                    label = "GPU Sharpness",
                    value = state.adjustments.sharpness,
                    range = 0.0f..2.0f,
                    onValueChange = {
                        viewModel.updateAdjustments(state.adjustments.copy(sharpness = it))
                    }
                )
                AdjustmentSlider(
                    label = "Warmth",
                    value = state.adjustments.warmth,
                    range = -1.0f..1.0f,
                    onValueChange = {
                        viewModel.updateAdjustments(state.adjustments.copy(warmth = it))
                    }
                )
                AdjustmentSlider(
                    label = "Vignette",
                    value = state.adjustments.vignette,
                    range = 0.0f..1.0f,
                    onValueChange = {
                        viewModel.updateAdjustments(state.adjustments.copy(vignette = it))
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StylePresets.all.forEach { preset ->
                    FilterChip(
                        selected = state.activePreset == preset.name,
                        onClick = { viewModel.applyPreset(preset) },
                        label = {
                            Text(
                                text = preset.name,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = Color(0xFFE2E8F0), fontSize = 12.sp)
            Text(text = "%.2f".format(value), color = Color(0xFF38BDF8), fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.height(28.dp)
        )
    }
}
