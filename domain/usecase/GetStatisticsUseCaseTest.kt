package com.example.todo.domain.usecase

import com.example.todo.domain.model.Task
import com.example.todo.domain.repository.TasksRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * GetStatisticsUseCase에 대한 JVM 단위 테스트입니다.
 * MockK를 사용하여 Repository의 동작을 모의(Mocking)합니다.
 */
class GetStatisticsUseCaseTest {

    private lateinit var tasksRepository: TasksRepository
    private lateinit var getStatisticsUseCase: GetStatisticsUseCase

    @Before
    fun setup() {
        tasksRepository = mockk()
        getStatisticsUseCase = GetStatisticsUseCase(tasksRepository)
    }

    @Test
    fun `태스크가 없을 때_모든 통계가 0이고 isEmpty가 true여야 한다`() = runTest {
        // Given: 빈 태스크 리스트 반환
        coEvery { tasksRepository.getTasksStream() } returns flowOf(emptyList())

        // When: 통계 계산 요청
        val result = getStatisticsUseCase().first()

        // Then: 결과 검증
        assertEquals(true, result.isEmpty)
        assertEquals(0f, result.activeTasksPercent, 0f)
        assertEquals(0f, result.completedTasksPercent, 0f)
        assertEquals(0, result.activeTasksCount)
        assertEquals(0, result.completedTasksCount)
    }

    @Test
    fun `활성 태스크만 있을 때_활성비율 100퍼센트여야 한다`() = runTest {
        // Given: 활성 태스크 1개
        val activeTask = Task(id = "1", title = "Task 1", isCompleted = false)
        coEvery { tasksRepository.getTasksStream() } returns flowOf(listOf(activeTask))

        // When
        val result = getStatisticsUseCase().first()

        // Then
        assertEquals(false, result.isEmpty)
        assertEquals(100f, result.activeTasksPercent, 0f)
        assertEquals(0f, result.completedTasksPercent, 0f)
        assertEquals(1, result.activeTasksCount)
        assertEquals(0, result.completedTasksCount)
    }

    @Test
    fun `완료 태스크만 있을 때_완료비율 100퍼센트여야 한다`() = runTest {
        // Given: 완료 태스크 1개
        val completedTask = Task(id = "1", title = "Task 1", isCompleted = true)
        coEvery { tasksRepository.getTasksStream() } returns flowOf(listOf(completedTask))

        // When
        val result = getStatisticsUseCase().first()

        // Then
        assertEquals(false, result.isEmpty)
        assertEquals(0f, result.activeTasksPercent, 0f)
        assertEquals(100f, result.completedTasksPercent, 0f)
        assertEquals(0, result.activeTasksCount)
        assertEquals(1, result.completedTasksCount)
    }

    @Test
    fun `활성3 완료2 태스크가 있을 때_정확한 비율을 계산해야 한다`() = runTest {
        // Given: 총 5개 (활성 3, 완료 2)
        val tasks = listOf(
            Task(id = "1", title = "Active 1", isCompleted = false),
            Task(id = "2", title = "Active 2", isCompleted = false),
            Task(id = "3", title = "Active 3", isCompleted = false),
            Task(id = "4", title = "Completed 1", isCompleted = true),
            Task(id = "5", title = "Completed 2", isCompleted = true)
        )
        coEvery { tasksRepository.getTasksStream() } returns flowOf(tasks)

        // When
        val result = getStatisticsUseCase().first()

        // Then
        // 활성: 3/5 = 60%, 완료: 2/5 = 40%
        assertEquals(false, result.isEmpty)
        assertEquals(60f, result.activeTasksPercent, 0f)
        assertEquals(40f, result.completedTasksPercent, 0f)
        assertEquals(3, result.activeTasksCount)
        assertEquals(2, result.completedTasksCount)
    }
}