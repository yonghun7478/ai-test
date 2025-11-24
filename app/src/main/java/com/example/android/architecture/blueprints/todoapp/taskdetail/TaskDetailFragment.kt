/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.architecture.blueprints.todoapp.taskdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.TodoViewModelFactory
import com.example.android.architecture.blueprints.todoapp.databinding.TaskdetailFragBinding
import com.example.android.architecture.blueprints.todoapp.tasks.DELETE_RESULT_OK
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Main UI for the task detail screen.
 */
class TaskDetailFragment : Fragment() {
    private lateinit var viewDataBinding: TaskdetailFragBinding

    private val args: TaskDetailFragmentArgs by navArgs()

    private val viewModel by viewModels<TaskDetailViewModel> { TodoViewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFab()
        setupListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        updateUi(uiState)
                    }
                }

                launch {
                    viewModel.snackbarText.collect { msgId ->
                        Snackbar.make(view, msgId, Snackbar.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.editTaskEvent.collect {
                        val action = TaskDetailFragmentDirections
                            .actionTaskDetailFragmentToAddEditTaskFragment(
                                args.taskId,
                                resources.getString(R.string.edit_task)
                            )
                        findNavController().navigate(action)
                    }
                }

                launch {
                    viewModel.deleteTaskEvent.collect {
                        val action = TaskDetailFragmentDirections
                            .actionTaskDetailFragmentToTasksFragment(DELETE_RESULT_OK)
                        findNavController().navigate(action)
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        viewDataBinding.refreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }
        viewDataBinding.taskDetailCompleteCheckbox.setOnClickListener {
            val checkBox = it as CheckBox
            viewModel.setCompleted(checkBox.isChecked)
        }
    }

    private fun updateUi(uiState: TaskDetailUiState) {
        val binding = viewDataBinding

        // Refresh Layout
        binding.refreshLayout.isRefreshing = uiState.isLoading

        if (uiState.task != null) {
            binding.noTaskLayout.visibility = View.GONE
            binding.taskDetailLayout.visibility = View.VISIBLE

            binding.taskDetailTitleText.text = uiState.task.title
            binding.taskDetailDescriptionText.text = uiState.task.description
            // Avoid infinite loop if setChecked triggers listener?
            // CheckBox.setChecked(boolean) triggers OnCheckedChangeListener if set.
            // But we used OnClickListener in setupListeners, so setChecked() programmatically won't trigger onClick.
            binding.taskDetailCompleteCheckbox.isChecked = uiState.isTaskCompleted
        } else {
            binding.taskDetailLayout.visibility = View.GONE
            binding.noTaskLayout.visibility = View.VISIBLE

            // Check isLoading to hide "No Data" text if loading
            binding.noTaskText.visibility = if (uiState.isLoading) View.GONE else View.VISIBLE
        }
    }

    private fun setupFab() {
        activity?.findViewById<View>(R.id.edit_task_fab)?.setOnClickListener {
            viewModel.editTask()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.taskdetail_frag, container, false)
        viewDataBinding = TaskdetailFragBinding.bind(view)
        viewDataBinding.lifecycleOwner = this.viewLifecycleOwner

        viewModel.start(args.taskId)

        setupMenuProvider()

        return view
    }

    private fun setupMenuProvider() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.taskdetail_fragment_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.menu_delete -> {
                            viewModel.deleteTask()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner, Lifecycle.State.RESUMED
        )
    }
}
