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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the implementation of [TasksViewModel]
 */
@ExperimentalCoroutinesApi
class TasksViewModelTest {

    // Subject under test
    private lateinit var tasksViewModel: TasksViewModel

    // Use a fake repository to be injected into the viewmodel
    private lateinit var tasksRepository: FakeRepository

    // Set the main coroutines dispatcher for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(StandardTestDispatcher())

    // Executes each task synchronously using Architecture Components.
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setupViewModel() {
        // We initialise the tasks to 3, with one active and two completed
        tasksRepository = FakeRepository()
        val task1 = Task("Title1", "Description1")
        val task2 = Task("Title2", "Description2", true)
        val task3 = Task("Title3", "Description3", true)
        tasksRepository.addTasks(task1, task2, task3)

        tasksViewModel = TasksViewModel(tasksRepository, SavedStateHandle())
    }

    @Test
    fun `loadTasks_loadsTasksIntoUiState`() = runTest {
        // Given an initialized ViewModel
        // When loading of tasks is requested
        tasksViewModel.loadTasks(true)
        advanceUntilIdle()

        // Then the tasks are loaded into the uiState
        assertThat(tasksViewModel.uiState.value.items).hasSize(3)
        assertThat(tasksViewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `loadActiveTasksFromRepositoryAndLoadIntoView`() = runTest {
        // Given an initialized TasksViewModel with initialized tasks
        // When loading of Tasks is requested
        tasksViewModel.setFiltering(TasksFilterType.ACTIVE_TASKS)

        // Load tasks
        tasksViewModel.loadTasks(true)
        advanceUntilIdle()

        // Then progress indicator is hidden
        assertThat(tasksViewModel.uiState.value.isLoading).isFalse()

        // And data correctly loaded
        assertThat(tasksViewModel.uiState.value.items).hasSize(1)
    }

    @Test
    fun `loadCompletedTasksFromRepositoryAndLoadIntoView`() = runTest {
        // Given an initialized TasksViewModel with initialized tasks
        // When loading of Tasks is requested
        tasksViewModel.setFiltering(TasksFilterType.COMPLETED_TASKS)

        // Load tasks
        tasksViewModel.loadTasks(true)
        advanceUntilIdle()

        // Then progress indicator is hidden
        assertThat(tasksViewModel.uiState.value.isLoading).isFalse()

        // And data correctly loaded
        assertThat(tasksViewModel.uiState.value.items).hasSize(2)
    }

    @Test
    fun `loadTasks_error`() = runTest {
        // Make the repository return errors
        tasksRepository.setReturnError(true)

        val messages = mutableListOf<Int>()
        val job = launch {
            tasksViewModel.userMessage.collect { messages.add(it) }
        }

        // Load tasks
        tasksViewModel.loadTasks(true)
        advanceUntilIdle()

        // Then progress indicator is hidden
        assertThat(tasksViewModel.uiState.value.isLoading).isFalse()

        // And the list of items is empty
        assertThat(tasksViewModel.uiState.value.items).hasSize(0)

        // And the snackbar updated
        assertThat(messages).contains(R.string.loading_tasks_error)

        job.cancel()
    }

    @Test
    fun `clickOnFab_showsAddTaskUi`() = runTest {
        tasksViewModel.newTaskEvent.test {
            // When adding a new task
            tasksViewModel.addNewTask()
            // Then the event is triggered
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun `clickOnOpenTask_setsEvent`() = runTest {
        val taskId = "42"
        tasksViewModel.openTaskEvent.test {
            // When opening a new task
            tasksViewModel.openTask(taskId)
            // Then the event is triggered
            assertThat(awaitItem()).isEqualTo(taskId)
        }
    }

    @Test
    fun `clearCompletedTasks_clearsTasks`() = runTest {
        tasksViewModel.userMessage.test {
             // When completed tasks are cleared
            tasksViewModel.clearCompletedTasks()
            // Verify snackbar is updated
            assertThat(awaitItem()).isEqualTo(R.string.completed_tasks_cleared)
        }

        // Fetch tasks
        tasksViewModel.loadTasks(true)

        advanceUntilIdle()

        // Fetch tasks
        val allTasks = tasksViewModel.uiState.value.items
        val completedTasks = allTasks.filter { it.isCompleted }

        // Verify there are no completed tasks left
        assertThat(completedTasks).isEmpty()

        // Verify active task is not cleared
        assertThat(allTasks).hasSize(1)
    }

    @Test
    fun `showEditResultMessages_editOk_snackbarUpdated`() = runTest {
        tasksViewModel.userMessage.test {
            // When the viewmodel receives a result from another destination
            tasksViewModel.showEditResultMessage(EDIT_RESULT_OK)
            // The snackbar is updated
            assertThat(awaitItem()).isEqualTo(R.string.successfully_saved_task_message)
        }
    }

    @Test
    fun `showEditResultMessages_addOk_snackbarUpdated`() = runTest {
        tasksViewModel.userMessage.test {
            // When the viewmodel receives a result from another destination
            tasksViewModel.showEditResultMessage(ADD_EDIT_RESULT_OK)
            // The snackbar is updated
            assertThat(awaitItem()).isEqualTo(R.string.successfully_added_task_message)
        }
    }

    @Test
    fun `showEditResultMessages_deleteOk_snackbarUpdated`() = runTest {
        tasksViewModel.userMessage.test {
            // When the viewmodel receives a result from another destination
            tasksViewModel.showEditResultMessage(DELETE_RESULT_OK)
            // The snackbar is updated
            assertThat(awaitItem()).isEqualTo(R.string.successfully_deleted_task_message)
        }
    }

    @Test
    fun `completeTask_dataAndSnackbarUpdated`() = runTest {
        // With a repository that has an active task
        val task = Task("Title", "Description")
        tasksRepository.addTasks(task)

        tasksViewModel.userMessage.test {
            // Complete task
            tasksViewModel.completeTask(task, true)
            // The snackbar is updated
            assertThat(awaitItem()).isEqualTo(R.string.task_marked_complete)
        }

        // Verify the task is completed
        assertThat(tasksRepository.tasksServiceData[task.id]?.isCompleted).isTrue()
    }

    @Test
    fun `activateTask_dataAndSnackbarUpdated`() = runTest {
        // With a repository that has a completed task
        val task = Task("Title", "Description", true)
        tasksRepository.addTasks(task)

        tasksViewModel.userMessage.test {
            // Activate task
            tasksViewModel.completeTask(task, false)
            // The snackbar is updated
            assertThat(awaitItem()).isEqualTo(R.string.task_marked_active)
        }

        // Verify the task is active
        assertThat(tasksRepository.tasksServiceData[task.id]?.isActive).isTrue()
    }

    @Test
    fun `getTasksAddViewVisible`() {
        // When the filter type is ALL_TASKS
        tasksViewModel.setFiltering(TasksFilterType.ALL_TASKS)

        // Then the "Add task" action is visible
        assertThat(tasksViewModel.uiState.value.addTaskVisible).isTrue()
    }

    @Test
    fun `refresh_loadsTasks`() = runTest {
        // WHEN - Refresh is called
        tasksViewModel.refresh()
        advanceUntilIdle()

        // THEN - The tasks are loaded
        assertThat(tasksViewModel.uiState.value.items).hasSize(3)
        assertThat(tasksViewModel.uiState.value.isLoading).isFalse()
    }
}