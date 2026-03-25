package org.catrobat.catroid.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.catrobat.catroid.R
import org.catrobat.catroid.common.Constants
import org.catrobat.catroid.utils.ImageEditing
import java.io.File

class ProjectLauncherIconProvider(
    private val context: Context,
    private val iconSize: Int = 512
) {
    companion object {
        private const val CACHE_SIZE = 10
        private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
                return size > CACHE_SIZE
            }
        }

        data class CacheEntry(val bitmap: Bitmap, val lastModified: Long)
    }

    /**
     * Produces a bitmap for the project at the given directory.
     * Uses cache if available and up-to-date.
     */
    fun getLauncherIcon(projectDir: File): Bitmap {
        val cacheKey = projectDir.absolutePath
        val lastModified = getLatestModificationTime(projectDir)

        val cachedEntry = cache.get(cacheKey)
        if (cachedEntry != null && cachedEntry.lastModified == lastModified) {
            return cachedEntry.bitmap
        }

        val bitmap = createLauncherIcon(projectDir)
        cache.put(cacheKey, CacheEntry(bitmap, lastModified))
        return bitmap
    }

    private fun createLauncherIcon(projectDir: File): Bitmap {
        val screenshotFile = findScreenshotFile(projectDir)
        val bitmap = if (screenshotFile != null && screenshotFile.exists()) {
            try {
                ImageEditing.getScaledBitmapFromPath(
                    screenshotFile.absolutePath,
                    iconSize,
                    iconSize,
                    ImageEditing.ResizeType.FILL_RECTANGLE_WITH_SAME_ASPECT_RATIO,
                    false
                )
            } catch (e: Exception) {
                // We use println for JVM compatibility in tests
                println("Error loading screenshot: ${e.message}")
                getFallbackIcon()
            }
        } else {
            getFallbackIcon()
        }

        return bitmap ?: getFallbackIcon()
    }

    private fun findScreenshotFile(projectDir: File): File? {
        val manual = File(projectDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        if (manual.exists() && manual.length() > 0) return manual

        val automatic = File(projectDir, Constants.SCREENSHOT_AUTOMATIC_FILE_NAME)
        if (automatic.exists() && automatic.length() > 0) return automatic

        // Fallback to scene screenshots if project level screenshot is missing
        projectDir.listFiles()?.filter { it.isDirectory }?.forEach { sceneDir ->
            val sceneManual = File(sceneDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
            if (sceneManual.exists() && sceneManual.length() > 0) return sceneManual

            val sceneAutomatic = File(sceneDir, Constants.SCREENSHOT_AUTOMATIC_FILE_NAME)
            if (sceneAutomatic.exists() && sceneAutomatic.length() > 0) return sceneAutomatic
        }

        return null
    }

    private fun getFallbackIcon(): Bitmap {
        return BitmapFactory.decodeResource(context.resources, R.drawable.catrobat)
    }

    private fun getLatestModificationTime(projectDir: File): Long {
        var latest = projectDir.lastModified()
        val codeXml = File(projectDir, Constants.CODE_XML_FILE_NAME)
        if (codeXml.exists()) {
            latest = Math.max(latest, codeXml.lastModified())
        }
        return latest
    }
}
