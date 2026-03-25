package org.catrobat.catroid.ui.recyclerview.dialog

import android.graphics.Bitmap
import android.os.Build
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.catrobat.catroid.R
import org.catrobat.catroid.common.ProjectData
import org.catrobat.catroid.ui.ProjectLauncherIconProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class LauncherIconPreviewDialogTest {

    private lateinit var projectData: ProjectData
    private val projectName = "TestProject"
    private val projectDir = File("/tmp/test_project")

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        projectData = ProjectData(projectName, projectDir, 0.9, false)
        
        mockkStatic(ProjectLauncherIconProvider::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDialogViewsArePopulated() {
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        
        io.mockk.mockkConstructor(ProjectLauncherIconProvider::class)
        every { anyConstructed<ProjectLauncherIconProvider>().getLauncherIcon(any()) } returns mockBitmap

        val activityController = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = activityController.create().start().resume().get()
        val fragment = LauncherIconPreviewDialog.newInstance(projectData)
        
        fragment.show(activity.supportFragmentManager, "launcher_icon_preview")
        ShadowLooper.idleMainLooper()

        val rootView = fragment.view ?: throw IllegalStateException("Fragment view should not be null")
        val projectNameView = rootView.findViewById<TextView>(R.id.project_name_view)
        val iconView = rootView.findViewById<ShapeableImageView>(R.id.launcher_icon_view)
        val okButton = rootView.findViewById<MaterialButton>(R.id.ok_button)

        assertNotNull("Project Name View should not be null", projectNameView)
        assertNotNull("Icon View should not be null", iconView)
        assertNotNull("OK Button should not be null", okButton)

        assertEquals(projectName, projectNameView?.text.toString())
        assertEquals(activity.getString(android.R.string.ok), okButton?.text.toString())
    }
}
