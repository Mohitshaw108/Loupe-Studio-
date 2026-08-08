package com.loupestudio.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EditorTab { ADJUST, COLOR, STYLES }

class EditorViewModel : ViewModel() {

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    private val _adjustments = MutableStateFlow(Adjustments.NEUTRAL)
    val adjustments: StateFlow<Adjustments> = _adjustments.asStateFlow()

    private val _tab = MutableStateFlow(EditorTab.ADJUST)
    val tab: StateFlow<EditorTab> = _tab.asStateFlow()

    private val _styleIntensity = MutableStateFlow(1f) // 0.2..1.5, matches the web slider's 20%-150%
    val styleIntensity: StateFlow<Float> = _styleIntensity.asStateFlow()

    private val _activeCategory = MutableStateFlow("All")
    val activeCategory: StateFlow<String> = _activeCategory.asStateFlow()

    private val _promptMatchLabel = MutableStateFlow<String?>(null)
    val promptMatchLabel: StateFlow<String?> = _promptMatchLabel.asStateFlow()

    // Simple linear undo/redo over full Adjustments snapshots — mirrors the web
    // build's pushHistory() but is dramatically simpler since we don't need to
    // snapshot raster pixels: adjustments are non-destructive until export.
    private val undoStack = ArrayDeque<Adjustments>()
    private val redoStack = ArrayDeque<Adjustments>()

    fun loadImage(bmp: Bitmap) {
        _bitmap.value = bmp
        _adjustments.value = Adjustments.NEUTRAL
        undoStack.clear(); redoStack.clear()
    }

    fun selectTab(t: EditorTab) { _tab.value = t }

    private fun pushUndo() {
        undoStack.addLast(_adjustments.value)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_adjustments.value)
        _adjustments.value = prev
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_adjustments.value)
        _adjustments.value = next
    }

    /** Called continuously while a slider drags — cheap, no history push per-frame. */
    fun updateLive(transform: (Adjustments) -> Adjustments) {
        _adjustments.update(transform)
    }

    /** Called once when a slider is released, so undo has one entry per gesture. */
    fun commitGesture(beforeGesture: Adjustments) {
        undoStack.addLast(beforeGesture)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
    }

    fun resetAdjustments() {
        pushUndo()
        _adjustments.value = Adjustments.NEUTRAL
    }

    fun selectCategory(cat: String) { _activeCategory.value = cat }

    fun setStyleIntensity(v: Float) { _styleIntensity.value = v }

    fun applyStyle(preset: StylePreset) {
        pushUndo()
        _adjustments.value = preset.base.scaledBy(_styleIntensity.value)
        _promptMatchLabel.value = preset.name + " applied"
    }

    fun applyPrompt(text: String) {
        val (adj, matched) = StylePresets.matchPrompt(text)
        pushUndo()
        _adjustments.value = adj.scaledBy(_styleIntensity.value)
        _promptMatchLabel.value = if (matched > 0) {
            "Style ($matched cue${if (matched > 1) "s" else ""} matched)"
        } else "Generic enhance"
    }

    fun filteredStyles(): List<StylePreset> {
        val cat = _activeCategory.value
        return if (cat == "All") StylePresets.styles else StylePresets.styles.filter { it.category == cat }
    }
}
