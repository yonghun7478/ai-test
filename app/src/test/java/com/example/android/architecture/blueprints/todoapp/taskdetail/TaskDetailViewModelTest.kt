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

import app.cash.turbine.test
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.data.Result.Success
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the implementation of [TaskDetailViewModel]
 */
@ExperimentalCoroutinesApi
class TaskDetailViewModelTest {

    // Subject under test
    private lateinit var taskDetailViewModel: TaskDetailViewModel

    // Use a fake repository to be injected into the viewmodel
    private lateinit var tasksRepository: FakeRepository

    // Set the main coroutines dispatcher for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    val task = Task("Title1", "Description1")

    @Before
    fun setupViewModel() {
        tasksRepository = FakeRepository()
        tasksRepository.addTasks(task)

        taskDetailViewModel = TaskDetailViewModel(tasksRepository)
    }

    @Test
    fun getActiveTaskFromRepositoryAndLoadIntoView() = runTest {
        taskDetailViewModel.start(task.id)

        taskDetailViewModel.uiState.test {
            // Loaded state (Initial loading skipped because FakeRepository emits immediately)
            val loadedState = awaitItem()
            assertThat(loadedState.task?.title).isEqualTo(task.title)
            assertThat(loadedState.task?.description).isEqualTo(task.description)
            assertThat(loadedState.isLoading).isFalse()
        }
    }

    @Test
    fun completeTask() = runTest {
        // Load the ViewModel
        taskDetailViewModel.start(task.id)

        // Verify that the task was active initially
        assertThat(tasksRepository.tasksServiceData[task.id]?.isCompleted).isFalse()

        taskDetailViewModel.uiState.test {
            awaitItem() // Loaded

            taskDetailViewModel.setCompleted(true)

            // Should emit new state with completed = true
            val completedState = awaitItem()
            assertThat(completedState.isTaskCompleted).isTrue()
        }

        // Then the task is completed and the snackbar shows the correct message
        assertThat(tasksRepository.tasksServiceData[task.id]?.isCompleted).isTrue()
    }

        @Test
        fun completeTask_showsSnackbar() = runTest {
            taskDetailViewModel.start(task.id)
            // Ensure uiState is collected so that it updates and populates .value
            val job = launch { taskDetailViewModel.uiState.collect {} }
            advanceUntilIdle() // Ensure loaded
    
            taskDetailViewModel.snackbarText.test {
                taskDetailViewModel.setCompleted(true)
                assertThat(awaitItem()).isEqualTo(R.string.task_marked_complete)
            }
            job.cancel()
        }
    
        @Test
        fun activateTask() = runTest {
            task.isCompleted = true
    
            // Load the ViewModel
            taskDetailViewModel.start(task.id)
    
            // Verify that the task was completed initially
            assertThat(tasksRepository.tasksServiceData[task.id]?.isCompleted).isTrue()
    
            taskDetailViewModel.uiState.test {
                 awaitItem() // Loaded
    
                 // When the ViewModel is asked to complete the task
                 taskDetailViewModel.setCompleted(false)
    
                 val activeState = awaitItem()
                 assertThat(activeState.isTaskCompleted).isFalse()
            }
    
            // Then the task is not completed
            val newTask = (tasksRepository.getTask(task.id) as Success).data
            assertTrue(newTask.isActive)
        }
    
        @Test
        fun activateTask_showsSnackbar() = runTest {
            task.isCompleted = true
            taskDetailViewModel.start(task.id)
            val job = launch { taskDetailViewModel.uiState.collect {} }
            advanceUntilIdle()
    
            taskDetailViewModel.snackbarText.test {
                taskDetailViewModel.setCompleted(false)
                assertThat(awaitItem()).isEqualTo(R.string.task_marked_active)
            }
            job.cancel()
        }
    
        @Test
        fun taskDetailViewModel_repositoryError() = runTest {
            // Given a repository that returns errors
            tasksRepository.setReturnError(true)
    
            // Given an initialized ViewModel with an active task
            taskDetailViewModel.start(task.id)
    
            taskDetailViewModel.uiState.test {
                 // Error state. The task is null.
                 val errorState = awaitItem()
                 assertThat(errorState.task).isNull()
                 assertThat(errorState.isLoading).isFalse()
            }
        }
    
        @Test
        fun taskDetailViewModel_repositoryError_showsSnackbar() = runTest {
            tasksRepository.setReturnError(true)
    
            // We must collect uiState to trigger the side effect in combine
            val job = launch { taskDetailViewModel.uiState.collect {} }
            
            taskDetailViewModel.snackbarText.test {
                taskDetailViewModel.start(task.id)
                assertThat(awaitItem()).isEqualTo(R.string.loading_tasks_error)
            }
            job.cancel()
        }
    @Test
    fun clickOnEditTask_SetsEvent() = runTest {
        taskDetailViewModel.editTaskEvent.test {
            taskDetailViewModel.editTask()
            assertThat(awaitItem()).isNotNull()
        }
    }

    @Test
    fun deleteTask() = runTest {
        assertThat(tasksRepository.tasksServiceData.containsValue(task)).isTrue()
        taskDetailViewModel.start(task.id)

        taskDetailViewModel.deleteTaskEvent.test {
            taskDetailViewModel.deleteTask()
            assertThat(awaitItem()).isNotNull()
        }

        assertThat(tasksRepository.tasksServiceData.containsValue(task)).isFalse()
    }

    @Test
    fun loadTask_loading() = runTest {
        taskDetailViewModel.start(task.id)

        taskDetailViewModel.uiState.test {
            assertThat(awaitItem().isLoading).isFalse() // Loaded

            taskDetailViewModel.refresh()

            assertThat(awaitItem().isLoading).isTrue() // Refreshing
            assertThat(awaitItem().isLoading).isFalse() // Refreshed
        }
    }
}