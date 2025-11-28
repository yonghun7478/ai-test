package com.example.todo.statistics

/**
 * 통계 화면의 UI 상태를 나타내는 데이터 클래스입니다.
 *
 * @property isLoading 데이터 로딩 중 여부
 * @property activeTasksPercent 활성 작업의 백분율 (0f ~ 100f)
 * @property completedTasksPercent 완료된 작업의 백분율 (0f ~ 100f)
 * @property isEmpty 표시할 통계 데이터가 없는지 여부
 * @property isError 데이터 로드 중 에러 발생 여부
 */
data class StatisticsUiState(
    val isLoading: Boolean = false,
    val activeTasksPercent: Float = 0f,
    val completedTasksPercent: Float = 0f,
    val isEmpty: Boolean = false,
    val isError: Boolean = false
) {
    companion object {
        /**
         * 화면 진입 시 사용할 초기 상태입니다.
         * 일반적으로 데이터를 로드하기 시작하므로 isLoading을 true로 설정할 수도 있으나,
         * 여기서는 가장 기본적인 '비어있는' 상태를 정의하고 ViewModel에서 로딩을 시작하도록 합니다.
         */
        val Initial = StatisticsUiState(
            isLoading = false,
            activeTasksPercent = 0f,
            completedTasksPercent = 0f,
            isEmpty = false,
            isError = false
        )
    }
}
