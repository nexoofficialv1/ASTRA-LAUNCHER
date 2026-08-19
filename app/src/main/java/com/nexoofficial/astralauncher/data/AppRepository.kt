package com.nexoofficial.astralauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import com.nexoofficial.astralauncher.model.LauncherApp
import java.text.Collator
import java.util.Locale

class AppRepository(private val context: Context) {

    fun loadLaunchableApps(): List<LauncherApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }

        val collator = Collator.getInstance(Locale.getDefault())

        return activities
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { resolveInfo ->
                runCatching {
                    val activityInfo = resolveInfo.activityInfo
                    LauncherApp(
                        label = resolveInfo.loadLabel(packageManager).toString().trim(),
                        packageName = activityInfo.packageName,
                        componentName = android.content.ComponentName(
                            activityInfo.packageName,
                            activityInfo.name
                        ),
                        icon = drawableToBitmap(resolveInfo.loadIcon(packageManager)).asImageBitmap()
                    )
                }.getOrNull()
            }
            .distinctBy { it.componentName }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
