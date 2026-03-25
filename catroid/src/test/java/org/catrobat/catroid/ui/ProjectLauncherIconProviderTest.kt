package org.catrobat.catroid.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.mockk.*
import org.catrobat.catroid.R
import org.catrobat.catroid.common.Constants
import org.catrobat.catroid.utils.ImageEditing
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectLauncherIconProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var resources: Resources
    private lateinit var projectDir: File
    private lateinit var provider: ProjectLauncherIconProvider

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        every { context.resources } returns resources
        
        projectDir = tempFolder.newFolder("test_project")
        
        mockkStatic(BitmapFactory::class)
        mockkStatic(ImageEditing::class)
        every { BitmapFactory.decodeResource(any(), any()) } returns mockk()
        
        provider = ProjectLauncherIconProvider(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testProviderReturnsIconWhenThumbnailExists() {
        val manualScreenshot = File(projectDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        manualScreenshot.writeText("fake image content")

        val mockBitmap = mockk<Bitmap>()
        every { 
            ImageEditing.getScaledBitmapFromPath(eq(manualScreenshot.absolutePath), eq(512), eq(512), any(), eq(false)) 
        } returns mockBitmap

        val result = provider.getLauncherIcon(projectDir)
        
        assertSame(mockBitmap, result)
        verify { ImageEditing.getScaledBitmapFromPath(eq(manualScreenshot.absolutePath), 512, 512, any(), false) }
    }

    @Test
    fun testProviderReturnsFallbackWhenThumbnailMissing() {
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val result = provider.getLauncherIcon(projectDir)

        assertSame(fallbackBitmap, result)
        verify { BitmapFactory.decodeResource(resources, R.drawable.catrobat) }
    }

    @Test
    fun testCacheHitBehavior() {
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        // First call
        val result1 = provider.getLauncherIcon(projectDir)
        
        // Second call
        val result2 = provider.getLauncherIcon(projectDir)

        assertSame(result1, result2)
        verify(exactly = 1) { BitmapFactory.decodeResource(any(), any()) }
    }
}
