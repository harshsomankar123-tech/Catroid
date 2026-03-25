package org.catrobat.catroid.ui.recyclerview.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.catrobat.catroid.R
import org.catrobat.catroid.common.ProjectData
import org.catrobat.catroid.databinding.DialogLauncherIconPreviewBinding
import org.catrobat.catroid.ui.ProjectLauncherIconProvider

class LauncherIconPreviewDialog : DialogFragment() {

    private var _binding: DialogLauncherIconPreviewBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_PROJECT_DATA = "project_data"

        fun newInstance(projectData: ProjectData): LauncherIconPreviewDialog {
            val args = Bundle().apply {
                putSerializable(ARG_PROJECT_DATA, projectData)
            }
            return LauncherIconPreviewDialog().apply {
                arguments = args
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLauncherIconPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val projectData = arguments?.getSerializable(ARG_PROJECT_DATA) as? ProjectData ?: return

        binding.projectNameView.text = projectData.name

        val provider = ProjectLauncherIconProvider(requireContext())
        val icon = provider.getLauncherIcon(projectData.directory)
        binding.launcherIconView.setImageBitmap(icon)

        binding.okButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setTitle(R.string.launcher_icon_preview)
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
