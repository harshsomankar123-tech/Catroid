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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.max

class ProjectLauncherIconProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var resources: Resources
    private lateinit var projectDir: File
    private lateinit var provider: ProjectLauncherIconProvider
    private val iconSize = 512

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        every { context.resources } returns resources
        
        projectDir = tempFolder.newFolder("test_project")
        
        mockkStatic(BitmapFactory::class)
        mockkStatic(ImageEditing::class)
        
        provider = ProjectLauncherIconProvider(context, iconSize)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testProviderReturnsIconWhenManualThumbnailExists() {
        val manualScreenshot = File(projectDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        manualScreenshot.writeText("fake image content")

        val mockBitmap = mockk<Bitmap>()
        every { 
            ImageEditing.getScaledBitmapFromPath(eq(manualScreenshot.absolutePath), eq(iconSize), eq(iconSize), any(), eq(false)) 
        } returns mockBitmap

        val result = provider.getLauncherIcon(projectDir)
        
        assertSame(mockBitmap, result)
    }

    @Test
    fun testProviderReturnsIconWhenAutomaticThumbnailExistsIfManualIsMissing() {
        val autoScreenshot = File(projectDir, Constants.SCREENSHOT_AUTOMATIC_FILE_NAME)
        autoScreenshot.writeText("fake image content")

        val mockBitmap = mockk<Bitmap>()
        every { 
            ImageEditing.getScaledBitmapFromPath(eq(autoScreenshot.absolutePath), eq(iconSize), eq(iconSize), any(), eq(false)) 
        } returns mockBitmap

        val result = provider.getLauncherIcon(projectDir)
        
        assertSame(mockBitmap, result)
    }

    @Test
    fun testProviderSkipsEmptyFilesAtProjectLevel() {
        val manualEmpty = File(projectDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        manualEmpty.createNewFile() // length is 0

        val autoEmpty = File(projectDir, Constants.SCREENSHOT_AUTOMATIC_FILE_NAME)
        autoEmpty.createNewFile() // length is 0

        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val result = provider.getLauncherIcon(projectDir)
        
        assertSame(fallbackBitmap, result)
    }

    @Test
    fun testProviderReturnsIconWhenSceneManualThumbnailExists() {
        // Create an empty dir and a non-directory file to test filters
        tempFolder.newFolder("test_project", "empty_dir")
        val fakeFile = File(projectDir, "not_a_dir.txt")
        fakeFile.writeText("hello")
        
        val sceneDir = tempFolder.newFolder("test_project", "Scene 1")
        val sceneManualScreenshot = File(sceneDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        sceneManualScreenshot.writeText("fake image content")

        val mockBitmap = mockk<Bitmap>()
        every { 
            ImageEditing.getScaledBitmapFromPath(eq(sceneManualScreenshot.absolutePath), eq(iconSize), eq(iconSize), any(), eq(false)) 
        } returns mockBitmap

        val result = provider.getLauncherIcon(projectDir)
        
        assertSame(mockBitmap, result)
    }

    @Test
    fun testProviderReturnsIconWhenSceneAutomaticThumbnailExistsIfManualMissing() {
        val sceneDir = tempFolder.newFolder("test_project", "Scene 1")
        val sceneAutoScreenshot = File(sceneDir, Constants.SCREENSHOT_AUTOMATIC_FILE_NAME)
        sceneAutoScreenshot.writeText("fake image content")

        val mockBitmap = mockk<Bitmap>()
        every { 
            ImageEditing.getScaledBitmapFromPath(eq(sceneAutoScreenshot.absolutePath), eq(iconSize), eq(iconSize), any(), eq(false)) 
        } returns mockBitmap

        val result = provider.getLauncherIcon(projectDir)
        
        assertSame(mockBitmap, result)
    }

    @Test
    fun testProviderSkipsEmptyFilesAtSceneLevel() {
        val sceneDir = tempFolder.newFolder("test_project", "Scene 1")
        val sceneManualEmpty = File(sceneDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        sceneManualEmpty.createNewFile()
        val sceneAutoEmpty = File(sceneDir, Constants.SCREENSHOT_AUTOMATIC_FILE_NAME)
        sceneAutoEmpty.createNewFile()

        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val result = provider.getLauncherIcon(projectDir)
        
        assertSame(fallbackBitmap, result)
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
    fun testProviderReturnsFallbackWhenBitmapLoadingFails() {
        val manualScreenshot = File(projectDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        manualScreenshot.writeText("fake image content")

        every { 
            ImageEditing.getScaledBitmapFromPath(any(), any(), any(), any(), any()) 
        } throws RuntimeException("Load failed")

        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val result = provider.getLauncherIcon(projectDir)

        assertSame(fallbackBitmap, result)
    }

    @Test
    fun testProviderReturnsFallbackWhenBitmapReturnedIsNull() {
        val manualScreenshot = File(projectDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        manualScreenshot.writeText("fake image content")

        every { 
            ImageEditing.getScaledBitmapFromPath(any(), any(), any(), any(), any()) 
        } returns null

        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val result = provider.getLauncherIcon(projectDir)

        assertSame(fallbackBitmap, result)
    }

    @Test
    fun testModificationTimeIncludesCodeXml() {
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val codeXml = File(projectDir, Constants.CODE_XML_FILE_NAME)
        codeXml.writeText("fake code")
        
        // Ensure code.xml is NEWER than projectDir
        projectDir.setLastModified(1000)
        codeXml.setLastModified(2000)
        
        provider.getLauncherIcon(projectDir)
        
        // Update code.xml to even newer
        codeXml.setLastModified(3000)
        provider.getLauncherIcon(projectDir)
        
        verify(exactly = 2) { BitmapFactory.decodeResource(any(), any()) }
    }

    @Test
    fun testModificationTimeUsesProjectDirIfNewerThanCodeXml() {
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val codeXml = File(projectDir, Constants.CODE_XML_FILE_NAME)
        codeXml.writeText("fake code")
        
        // Ensure projectDir is NEWER than code.xml
        projectDir.setLastModified(2000)
        codeXml.setLastModified(1000)
        
        provider.getLauncherIcon(projectDir)
        
        // Update projectDir to even newer
        projectDir.setLastModified(3000)
        provider.getLauncherIcon(projectDir)
        
        verify(exactly = 2) { BitmapFactory.decodeResource(any(), any()) }
    }

    @Test
    fun testCacheHitBehavior() {
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        // First call - Cache miss
        val result1 = provider.getLauncherIcon(projectDir)
        
        // Second call - Cache hit
        val result2 = provider.getLauncherIcon(projectDir)

        assertSame(result1, result2)
        verify(exactly = 1) { BitmapFactory.decodeResource(any(), any()) }
    }

    @Test
    fun testCacheEviction() {
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        // Fill cache with 11 different projects to trigger eviction (limit is 10)
        val projectDirs = List(11) { tempFolder.newFolder("project_$it") }
        
        projectDirs.forEach { provider.getLauncherIcon(it) }
        
        // The first one should have been evicted
        provider.getLauncherIcon(projectDirs[0])
        
        // Total decode calls: 11 (one for each project) + 1 (after eviction) = 12
        verify(exactly = 12) { BitmapFactory.decodeResource(any(), any()) }
    }

    @Test
    fun testModificationTimeWhenCodeXmlMissing() {
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        // Ensure projectDir has a modification time but code.xml is missing
        projectDir.setLastModified(123456789L)
        
        val result = provider.getLauncherIcon(projectDir)
        assertNotNull(result)
    }

    @Test
    fun testProviderWhenProjectDirIsNotADirectory() {
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val fakeFile = File(projectDir, "not_a_real_project_dir.txt")
        fakeFile.writeText("dummy content")

        // Passing a file instead of a directory to cause listFiles() to return null
        val result = provider.getLauncherIcon(fakeFile)
        
        assertNotNull(result)
        assertSame(fallbackBitmap, result)
    }

    @Test
    fun testFindScreenshotFileReturnsNullWhenScenesHaveNoScreenshots() {
        // Create multiple scene directories but none should have a valid screenshot
        tempFolder.newFolder("test_project", "Scene 1")
        tempFolder.newFolder("test_project", "Scene 2")
        
        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val result = provider.getLauncherIcon(projectDir)
        
        assertSame(fallbackBitmap, result)
        // This ensures the forEach loop in findScreenshotFile finishes without returning
    }

    @Test
    fun testProviderWhenBitmapReturnedIsNullExplicitly() {
        val manualScreenshot = File(projectDir, Constants.SCREENSHOT_MANUAL_FILE_NAME)
        manualScreenshot.writeText("fake image content")

        // Mock ImageEditing.getScaledBitmapFromPath to return null to hit the ?: branch
        every { 
            ImageEditing.getScaledBitmapFromPath(any(), any(), any(), any(), any()) 
        } returns null

        val fallbackBitmap = mockk<Bitmap>()
        every { BitmapFactory.decodeResource(any(), any()) } returns fallbackBitmap

        val result = provider.getLauncherIcon(projectDir)

        assertSame(fallbackBitmap, result)
    }
}
