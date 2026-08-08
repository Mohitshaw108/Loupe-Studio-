package com.mohitshaw.loupestudio

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.mohitshaw.loupestudio.ui.EditorScreen
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runOnUiThread {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Crash")
                    .setMessage(throwable.stackTraceToString())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val stream = contentResolver.openInputStream(uri)
            if (stream == null) {
                Toast.makeText(this, "Could not open image stream", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            val bmp = stream.use { BitmapFactory.decodeStream(it) }
            if (bmp == null) {
                Toast.makeText(this, "Decode failed - unsupported format?", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            Toast.makeText(this, "Loaded ${bmp.width}x${bmp.height}, sending to editor", Toast.LENGTH_LONG).show()
            vm.loadImage(bmp)
        }

        setContent {
            EditorScreen(
                viewModel = vm,
                onPickImage = { pickImage.launch("image/*") },
                onExport = { bmp -> saveToGallery(bmp) }
            )
        }
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val name = "loupe_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LoupeStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
            return
        }
        var out: OutputStream? = null
        try {
            out = resolver.openOutputStream(uri)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out!!)
        } finally {
            out?.close()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }
        Toast.makeText(this, "Saved to Pictures/LoupeStudio", Toast.LENGTH_SHORT).show()
    }
}
