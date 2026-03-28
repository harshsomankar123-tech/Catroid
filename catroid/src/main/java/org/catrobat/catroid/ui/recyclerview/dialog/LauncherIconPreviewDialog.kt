/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2025 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.ui.recyclerview.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
