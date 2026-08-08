package com.mohitshaw.loupestudio.ui

import android.graphics.Bitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohitshaw.loupestudio.Adjustments
import com.mohitshaw.loupestudio.EditorTab
import com.mohitshaw.loupestudio.EditorViewModel
import com.mohitshaw.loupestudio.StylePreset
import com.mohitshaw.loupestudio.StylePresets
import com.mohitshaw.loupestudio.gl.PhotoGLSurfaceView

@Composable
fun EditorScreen(
    viewModel: EditorViewModel = viewModel(),
    onPickImage: () -> Unit,
    onExport: (Bitmap) -> Unit
) {
    val bitmap by viewModel.bitmap.collectAsState()
    val adjustments by viewModel.adjustments.collectAsState()
    val tab by viewModel.tab.collectAsState()

    // Held across recompositions so the GL surface + renderer survive tab/slider churn.
    var glView by remember { mutableStateOf<PhotoGLSurfaceView?>(null) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx -> PhotoGLSurfaceView(ctx).also { glView = it } },
                update = { view ->
                    bitmap?.let { view.setBitmap(it) }
                    view.setAdjustments(adjustments)
                },
                modifier = Modifier.fillMaxSize()
            )

        TabRow(selectedTabIndex = tab.ordinal) {
            EditorTab.values().forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { viewModel.selectTab(t) },
                    text = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Surface(tonalElevation = 2.dp) {
            Column(Modifier.padding(16.dp).heightIn(max = 320.dp)) {
                when (tab) {
                    EditorTab.ADJUST -> AdjustPanel(viewModel, adjustments)
                    EditorTab.COLOR -> ColorPanel(viewModel, adjustments)
                    EditorTab.STYLES -> StylesPanel(viewModel)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onPickImage: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onPickImage) { Text("Open") }
        Row {
            TextButton(onClick = onUndo) { Text("Undo") }
            TextButton(onClick = onRedo) { Text("Redo") }
            Button(onClick = onExport) { Text("Export") }
        }
    }
}

/** A slider that pushes one undo entry per drag gesture, then streams live values to the ViewModel. */
@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -100f..100f,
    onLiveChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value.toInt().toString(), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onLiveChange, valueRange = range)
    }
}

@Composable
private fun AdjustPanel(vm: EditorViewModel, a: Adjustments) {
    var gestureStart by remember { mutableStateOf(a) }
    fun start() { gestureStart = a }
    fun commit() { vm.commitGesture(gestureStart) }

    AdjustSlider("Exposure", a.exposure) { v -> if (v == a.exposure) start(); vm.updateLive { it.copy(exposure = v) } }
    AdjustSlider("Contrast", a.contrast) { v -> vm.updateLive { it.copy(contrast = v) } }
    AdjustSlider("Highlights", a.highlights) { v -> vm.updateLive { it.copy(highlights = v) } }
    AdjustSlider("Shadows", a.shadows) { v -> vm.updateLive { it.copy(shadows = v) } }
    AdjustSlider("Saturation", a.saturation) { v -> vm.updateLive { it.copy(saturation = v) } }
    AdjustSlider("Temperature", a.temperature) { v -> vm.updateLive { it.copy(temperature = v) } }
    AdjustSlider("Sharpen", a.sharpen, 0f..100f) { v -> vm.updateLive { it.copy(sharpen = v) } }
    AdjustSlider("Vignette", a.vignette) { v -> vm.updateLive { it.copy(vignette = v) } }
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { vm.resetAdjustments() }) { Text("Reset") }
    }
}

@Composable
private fun ColorPanel(vm: EditorViewModel, a: Adjustments) {
    AdjustSlider("Split Tone Amount", a.splitAmount, 0f..100f) { v -> vm.updateLive { it.copy(splitAmount = v) } }
    AdjustSlider("Red Mix", a.mixR) { v -> vm.updateLive { it.copy(mixR = v) } }
    AdjustSlider("Green Mix", a.mixG) { v -> vm.updateLive { it.copy(mixG = v) } }
    AdjustSlider("Blue Mix", a.mixB) { v -> vm.updateLive { it.copy(mixB = v) } }
    Text(
        "Split-tone shadow/highlight colors use the defaults from the web build; " +
            "wire up two color pickers here to match c_splitShadow / c_splitHigh.",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun StylesPanel(vm: EditorViewModel) {
    val intensity by vm.styleIntensity.collectAsState()
    val category by vm.activeCategory.collectAsState()
    val matchLabel by vm.promptMatchLabel.collectAsState()
    var prompt by remember { mutableStateOf("") }

    Column(Modifier.padding(vertical = 4.dp)) {
        Text("Intensity: ${(intensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(value = intensity, onValueChange = vm::setStyleIntensity, valueRange = 0.2f..1.5f)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(StylePresets.categories) { cat ->
                FilterChip(selected = category == cat, onClick = { vm.selectCategory(cat) }, label = { Text(cat) })
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier.heightIn(max = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(vm.filteredStyles()) { style: StylePreset ->
                AssistChip(onClick = { vm.applyStyle(style) }, label = { Text(style.name) })
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it },
            label = { Text("Describe a style (e.g. \"golden hour, dreamy\")") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.applyPrompt(prompt) }, modifier = Modifier.align(Alignment.End)) { Text("Generate") }
        matchLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
