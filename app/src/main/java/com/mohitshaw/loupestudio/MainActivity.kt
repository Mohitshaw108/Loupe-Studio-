package com.mohitshaw.loupestudio

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LoupeApp()
            }
        }
    }
}

@Composable
fun LoupeApp() {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var brightness by remember { mutableStateOf(0f) } // -100..100
    var contrast by remember { mutableStateOf(0f) }   // -100..100
    var saturation by remember { mutableStateOf(0f) } // -100..100

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { input ->
                bitmap = BitmapFactory.decodeStream(input)
            }
            brightness = 0f; contrast = 0f; saturation = 0f
        }
    }

    val matrixValues = remember(brightness, contrast, saturation) {
        buildColorMatrixValues(brightness, contrast, saturation)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Loupe Studio") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorMatrixColorFilter(ColorMatrix(matrixValues)),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Button(onClick = { pickImage.launch("image/*") }) {
                        Text("Pick a photo")
                    }
                }
            }

            if (bitmap != null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LabeledSlider("Exposure", brightness) { brightness = it }
                    LabeledSlider("Contrast", contrast) { contrast = it }
                    LabeledSlider("Saturation", saturation) { saturation = it }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pickImage.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Retake") }
                        Button(
                            onClick = {
                                bitmap?.let { src ->
                                    saveBitmap(context, src, matrixValues)
                                    scope.launch { snackbarHostState.showSnackbar("Saved to gallery") }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}")
        Slider(value = value, onValueChange = onChange, valueRange = -100f..100f)
    }
}

/**
 * Builds a standard 4x5 color matrix (row-major, same layout Android's own
 * android.graphics.ColorMatrix and Compose's ColorMatrix both use) combining
 * brightness, contrast, and saturation into one pass.
 */
fun buildColorMatrixValues(brightness: Float, contrast: Float, saturation: Float): FloatArray {
    val c = 1f + contrast / 100f
    val t = (1f - c) * 128f + brightness * 1.5f
    val s = (1f + saturation / 100f).coerceAtLeast(0f)

    val lumR = 0.213f; val lumG = 0.715f; val lumB = 0.072f
    val sr = (1 - s) * lumR
    val sg = (1 - s) * lumG
    val sb = (1 - s) * lumB

    val saturationMatrix = floatArrayOf(
        sr + s, sg, sb, 0f, 0f,
        sr, sg + s, sb, 0f, 0f,
        sr, sg, sb + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    val contrastBrightnessMatrix = floatArrayOf(
        c, 0f, 0f, 0f, t,
        0f, c, 0f, 0f, t,
        0f, 0f, c, 0f, t,
        0f, 0f, 0f, 1f, 0f
    )
    return multiply4x5(contrastBrightnessMatrix, saturationMatrix)
}

private fun multiply4x5(a: FloatArray, b: FloatArray): FloatArray {
    val result = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var sum = 0f
            for (k in 0 until 4) sum += a[row * 5 + k] * b[k * 5 + col]
            if (col == 4) sum += a[row * 5 + 4]
            result[row * 5 + col] = sum
        }
    }
    return result
}

fun saveBitmap(context: android.content.Context, source: Bitmap, matrixValues: FloatArray) {
    val paint = Paint().apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(matrixValues))
    }
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(source, 0f, 0f, paint)

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "loupe_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Loupe Studio")
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let { resolver.openOutputStream(it)?.use { stream -> output.compress(Bitmap.CompressFormat.JPEG, 92, stream) } }
}
