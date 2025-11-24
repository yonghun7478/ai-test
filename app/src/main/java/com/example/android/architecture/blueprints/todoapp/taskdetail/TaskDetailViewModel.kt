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

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.data.Result
import com.example.android.architecture.blueprints.todoapp.data.Result.Success
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UiState for the Details screen.
 */
data class TaskDetailUiState(
    val task: Task? = null,
    val isLoading: Boolean = false,
    val isTaskCompleted: Boolean = false
)

/**
 * ViewModel for the Details screen.
 */
class TaskDetailViewModel(
    private val tasksRepository: TasksRepository
) : ViewModel() {

    private val _taskId = MutableStateFlow<String?>(null)

    // A manual refresh signal
    private val _isLoading = MutableStateFlow(false)

    private val _editTaskEvent = MutableSharedFlow<Unit>()
    val editTaskEvent = _editTaskEvent.asSharedFlow()

    private val _deleteTaskEvent = MutableSharedFlow<Unit>()
    val deleteTaskEvent = _deleteTaskEvent.asSharedFlow()

    private val _snackbarText = MutableSharedFlow<Int>()
    val snackbarText = _snackbarText.asSharedFlow()

    val uiState: StateFlow<TaskDetailUiState> = _taskId.flatMapLatest { taskId ->
        if (taskId == null) {
            flowOf(TaskDetailUiState(isLoading = false))
        } else {
            combine(
                tasksRepository.observeTask(taskId),
                _isLoading
            ) { taskResult, isLoading ->
                val task = (taskResult as? Success)?.data
                // If repository emits Loading, we could show loading.
                // But mostly we rely on the manual loading or initial load.
                // Assuming observeTask might emit result.
                if (taskResult is Result.Error) {
                    _snackbarText.emit(R.string.loading_tasks_error)
                }
                TaskDetailUiState(
                    task = task,
                    isLoading = isLoading, // Or combine with taskResult is Loading
                    isTaskCompleted = task?.isCompleted ?: false
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskDetailUiState(isLoading = true)
    )

    fun deleteTask() = viewModelScope.launch {
        _taskId.value?.let {
            tasksRepository.deleteTask(it)
            _deleteTaskEvent.emit(Unit)
        }
    }

    fun editTask() = viewModelScope.launch {
        _editTaskEvent.emit(Unit)
    }

    fun setCompleted(completed: Boolean) = viewModelScope.launch {
        val task = uiState.value.task ?: return@launch
        if (completed) {
            tasksRepository.completeTask(task)
            showSnackbarMessage(R.string.task_marked_complete)
        } else {
            tasksRepository.activateTask(task)
            showSnackbarMessage(R.string.task_marked_active)
        }
    }

    fun start(taskId: String?) {
        // If we're already loading or already loaded, return (might be a config change)
        if (_taskId.value == taskId) {
            return
        }
        _taskId.value = taskId
    }

    fun refresh() {
        // Refresh the repository and the task will be updated automatically.
        val taskId = _taskId.value
        if (taskId != null) {
            _isLoading.value = true
            viewModelScope.launch {
                tasksRepository.refreshTask(taskId)
                _isLoading.value = false
            }
        }
    }

    private fun showSnackbarMessage(@StringRes message: Int) {
        viewModelScope.launch {
            _snackbarText.emit(message)
        }
    }
}
