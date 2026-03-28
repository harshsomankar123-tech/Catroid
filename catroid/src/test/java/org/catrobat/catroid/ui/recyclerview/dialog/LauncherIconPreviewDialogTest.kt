package org.catrobat.catroid.ui.recyclerview.dialog

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import io.mockk.*
import org.catrobat.catroid.R
import org.catrobat.catroid.common.ProjectData
import org.catrobat.catroid.ui.ProjectLauncherIconProvider
import org.catrobat.catroid.ui.recyclerview.fragment.ProjectListFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import java.io.File

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
    fun testFullInteractionFlow() {
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        mockkConstructor(ProjectLauncherIconProvider::class)
        every { anyConstructed<ProjectLauncherIconProvider>().getLauncherIcon(any()) } returns mockBitmap

        val activityController = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = activityController.create().start().resume().get()
        
        // Setup ProjectListFragment
        val projectListFragment = ProjectListFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, projectListFragment)
            .commitNow()
        
        ShadowLooper.idleMainLooper()

        // Simulate opening the settings menu and clicking 'Launcher icon preview'
        val mockView = View(activity)
        projectListFragment.onSettingsClick(projectData, mockView)
        ShadowLooper.idleMainLooper()

        // Since we can't easily click a real PopupMenu in Robolectric without complexity,
        // we directly call showLauncherIconPreviewDialog which is what the menu click does.
        ReflectionHelpers.callInstanceMethod<Any>(
            projectListFragment, 
            "showLauncherIconPreviewDialog", 
            ReflectionHelpers.ClassParameter(ProjectData::class.java, projectData)
        )
        ShadowLooper.idleMainLooper()

        // Assert dialog is shown
        val dialogFragment = projectListFragment.childFragmentManager.findFragmentByTag("LauncherIconPreviewDialog") 
            as? LauncherIconPreviewDialog
        assertNotNull("Dialog should be shown", dialogFragment)

        // Verify dialog contents
        val dialogView = dialogFragment!!.requireView()
        val projectNameView = dialogView.findViewById<TextView>(R.id.project_name_view)
        assertEquals(projectName, projectNameView?.text.toString())

        // Click OK button and verify dismissal
        val okButton = dialogView.findViewById<MaterialButton>(R.id.ok_button)
        okButton?.performClick()
        ShadowLooper.idleMainLooper()

        assertNull("Dialog should be dismissed", projectListFragment.childFragmentManager.findFragmentByTag("LauncherIconPreviewDialog"))
    }
}
