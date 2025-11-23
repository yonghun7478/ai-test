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
package com.example.android.architecture.blueprints.todoapp.tasks

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.architecture.blueprints.todoapp.Event
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.data.Result
import com.example.android.architecture.blueprints.todoapp.data.Result.Success
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.ACTIVE_TASKS
import com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.ALL_TASKS
import com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.COMPLETED_TASKS
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UiState for the task list screen.
 */
data class TasksUiState(
    val items: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    @StringRes val filteringLabel: Int = R.string.label_all,
    @StringRes val noTasksLabel: Int = R.string.no_tasks_all,
    @DrawableRes val noTaskIconRes: Int = R.drawable.logo_no_fill,
    val addTaskVisible: Boolean = true
)

/**
 * ViewModel for the task list screen.
 */
class TasksViewModel(
    private val tasksRepository: TasksRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<Int>()
    val userMessage: SharedFlow<Int> = _userMessage.asSharedFlow()

    private val _openTaskEvent = MutableSharedFlow<String>()
    val openTaskEvent: SharedFlow<String> = _openTaskEvent.asSharedFlow()

    private val _newTaskEvent = MutableSharedFlow<Unit>()
    val newTaskEvent: SharedFlow<Unit> = _newTaskEvent.asSharedFlow()

    private var resultMessageShown: Boolean = false

    init {
        // Set initial state if needed, but Flow will handle it
        loadTasks(true)

        viewModelScope.launch {
            combine(
                tasksRepository.observeTasks(),
                savedStateHandle.getStateFlow(TASKS_FILTER_SAVED_STATE_KEY, ALL_TASKS)
            ) { tasksResult, filteringType ->
                val tasks = if (tasksResult is Success) {
                    filterItems(tasksResult.data, filteringType)
                } else {
                    showSnackbarMessage(R.string.loading_tasks_error)
                    emptyList()
                }

                val (filteringLabel, noTasksLabel, noTaskIcon, addTaskVisible) = getFilterUiInfo(filteringType)

                TasksUiState(
                    items = tasks,
                    filteringLabel = filteringLabel,
                    noTasksLabel = noTasksLabel,
                    noTaskIconRes = noTaskIcon,
                    addTaskVisible = addTaskVisible,
                    isLoading = _uiState.value.isLoading // Keep current loading state
                )
            }
            .catch {
                showSnackbarMessage(R.string.loading_tasks_error)
            }
            .collect { newState ->
                _uiState.update { 
                    newState.copy(isLoading = it.isLoading) 
                }
            }
        }
    }

    /**
     * Sets the current task filtering type.
     *
     * @param requestType Can be [TasksFilterType.ALL_TASKS],
     * [TasksFilterType.COMPLETED_TASKS], or
     * [TasksFilterType.ACTIVE_TASKS]
     */
    fun setFiltering(requestType: TasksFilterType) {
        savedStateHandle[TASKS_FILTER_SAVED_STATE_KEY] = requestType
    }

    private fun getFilterUiInfo(requestType: TasksFilterType): FilterUiInfo {
        return when (requestType) {
            ALL_TASKS -> {
                FilterUiInfo(
                    R.string.label_all, R.string.no_tasks_all,
                    R.drawable.logo_no_fill, true
                )
            }
            ACTIVE_TASKS -> {
                FilterUiInfo(
                    R.string.label_active, R.string.no_tasks_active,
                    R.drawable.ic_check_circle_96dp, false
                )
            }
            COMPLETED_TASKS -> {
                FilterUiInfo(
                    R.string.label_completed, R.string.no_tasks_completed,
                    R.drawable.ic_verified_user_96dp, false
                )
            }
        }
    }

    data class FilterUiInfo(
        val filteringLabel: Int,
        val noTasksLabel: Int,
        val noTaskIcon: Int,
        val addTaskVisible: Boolean
    )

    fun clearCompletedTasks() {
        viewModelScope.launch {
            tasksRepository.clearCompletedTasks()
            showSnackbarMessage(R.string.completed_tasks_cleared)
        }
    }

    fun completeTask(task: Task, completed: Boolean) = viewModelScope.launch {
        if (completed) {
            tasksRepository.completeTask(task)
            showSnackbarMessage(R.string.task_marked_complete)
        } else {
            tasksRepository.activateTask(task)
            showSnackbarMessage(R.string.task_marked_active)
        }
    }

    /**
     * Called by the Data Binding library and the FAB's click listener.
     */
    fun addNewTask() {
        viewModelScope.launch {
            _newTaskEvent.emit(Unit)
        }
    }

    /**
     * Called by Data Binding.
     */
    fun openTask(taskId: String) {
        viewModelScope.launch {
            _openTaskEvent.emit(taskId)
        }
    }

    fun showEditResultMessage(result: Int) {
        if (resultMessageShown) return
        when (result) {
            EDIT_RESULT_OK -> showSnackbarMessage(R.string.successfully_saved_task_message)
            ADD_EDIT_RESULT_OK -> showSnackbarMessage(R.string.successfully_added_task_message)
            DELETE_RESULT_OK -> showSnackbarMessage(R.string.successfully_deleted_task_message)
        }
        resultMessageShown = true
    }

    private fun showSnackbarMessage(message: Int) {
        viewModelScope.launch {
            _userMessage.emit(message)
        }
    }

    /**
     * @param forceUpdate Pass in true to refresh the data in the [TasksDataSource]
     */
    fun loadTasks(forceUpdate: Boolean) {
        if (forceUpdate) {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                tasksRepository.refreshTasks()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun filterItems(tasks: List<Task>, filteringType: TasksFilterType): List<Task> {
        val tasksToShow = ArrayList<Task>()
        // We filter the tasks based on the requestType
        for (task in tasks) {
            when (filteringType) {
                ALL_TASKS -> tasksToShow.add(task)
                ACTIVE_TASKS -> if (task.isActive) {
                    tasksToShow.add(task)
                }
                COMPLETED_TASKS -> if (task.isCompleted) {
                    tasksToShow.add(task)
                }
            }
        }
        return tasksToShow
    }

    fun refresh() {
        loadTasks(true)
    }

    private fun getSavedFilterType(): TasksFilterType {
        return savedStateHandle.get(TASKS_FILTER_SAVED_STATE_KEY) ?: ALL_TASKS
    }
}

// Used to save the current filtering in SavedStateHandle.
const val TASKS_FILTER_SAVED_STATE_KEY = "TASKS_FILTER_SAVED_STATE_KEY"