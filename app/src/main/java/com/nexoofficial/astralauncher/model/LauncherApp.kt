package com.nexoofficial.astralauncher.model

import android.content.ComponentName
import androidx.compose.ui.graphics.ImageBitmap

data class LauncherApp(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val icon: ImageBitmap
)
