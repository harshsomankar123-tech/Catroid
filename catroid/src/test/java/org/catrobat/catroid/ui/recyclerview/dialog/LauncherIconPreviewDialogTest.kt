package org.catrobat.catroid.ui.recyclerview.dialog

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.AdapterView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import io.mockk.*
import org.catrobat.catroid.R
import org.catrobat.catroid.common.ProjectData
import org.catrobat.catroid.ui.ProjectLauncherIconProvider
import org.catrobat.catroid.ui.recyclerview.fragment.ProjectListFragment
import org.junit.After
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
    fun testLauncherIconPreviewFlow() {
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        mockkConstructor(ProjectLauncherIconProvider::class)
        every { anyConstructed<ProjectLauncherIconProvider>().getLauncherIcon(any()) } returns mockBitmap

        val activityController = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = activityController.create().start().resume().get()
        
        // 1. Setup ProjectListFragment with mock data
        val projectListFragment = ProjectListFragment()
        
        val itemListField = projectListFragment.javaClass.getDeclaredField("items")
        itemListField.isAccessible = true
        itemListField.set(projectListFragment, mutableListOf(projectData))

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, projectListFragment)
            .commitNow()
        
        ShadowLooper.idleMainLooper()

        // 2. Directly trigger the dialog creation method via reflection
        // This is more reliable than simulating PopupMenu in different Robolectric versions
        val showDialogMethod = projectListFragment.javaClass.getDeclaredMethod("showLauncherIconPreviewDialog", ProjectData::class.java)
        showDialogMethod.isAccessible = true
        showDialogMethod.invoke(projectListFragment, projectData)
        
        ShadowLooper.idleMainLooper()

        // 3. Assert the dialog is shown
        val dialogFragment = projectListFragment.childFragmentManager.findFragmentByTag("LauncherIconPreviewDialog") 
            as? LauncherIconPreviewDialog
        assertNotNull("Dialog should be shown", dialogFragment)

        val dialogView = dialogFragment!!.requireView()
        val projectNameView = dialogView.findViewById<TextView>(R.id.project_name_view)
        assertNotNull("Project name view should be visible", projectNameView)

        // 4. Tap OK
        val okButton = dialogView.findViewById<MaterialButton>(R.id.ok_button)
        okButton?.performClick()
        ShadowLooper.idleMainLooper()

        // 5. Assert the dialog is dismissed
        assertNull("Dialog should be dismissed", projectListFragment.childFragmentManager.findFragmentByTag("LauncherIconPreviewDialog"))
    }
}
