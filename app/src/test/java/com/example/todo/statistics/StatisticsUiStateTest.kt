package com.example.todo.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StatisticsUiState]에 대한 로컬 단위 테스트입니다.
 * 데이터 클래스의 생성, 초기값, 복사(copy) 기능이 올바르게 동작하는지 확인합니다.
 */
class StatisticsUiStateTest {

    @Test
    fun `StatisticsUiState_Initial_상태가_올바른_기본값을_가지는지_확인`() {
        // Given
        val state = StatisticsUiState.Initial

        // Then
        assertFalse("초기 로딩 상태는 false여야 합니다", state.isLoading)
        assertEquals("초기 활성 작업 퍼센트는 0f여야 합니다", 0f, state.activeTasksPercent, 0.0f)
        assertEquals("초기 완료 작업 퍼센트는 0f여야 합니다", 0f, state.completedTasksPercent, 0.0f)
        assertFalse("초기 empty 상태는 false여야 합니다", state.isEmpty)
        assertFalse("초기 에러 상태는 false여야 합니다", state.isError)
    }

    @Test
    fun `StatisticsUiState_copy_메서드가_데이터를_올바르게_변경하는지_확인`() {
        // Given
        val initialState = StatisticsUiState.Initial

        // When
        val loadedState = initialState.copy(
            isLoading = false,
            activeTasksPercent = 40f,
            completedTasksPercent = 60f,
            isEmpty = false,
            isError = false
        )

        // Then
        assertFalse(loadedState.isLoading)
        assertEquals(40f, loadedState.activeTasksPercent, 0.0f)
        assertEquals(60f, loadedState.completedTasksPercent, 0.0f)
        assertFalse(loadedState.isEmpty)
        assertFalse(loadedState.isError)
    }

    @Test
    fun `StatisticsUiState_에러_상태_생성_확인`() {
        // Given
        val errorState = StatisticsUiState(
            isLoading = false,
            isError = true
        )

        // Then
        assertTrue("에러 상태가 true여야 합니다", errorState.isError)
        assertFalse("로딩 상태가 false여야 합니다", errorState.isLoading)
    }
}