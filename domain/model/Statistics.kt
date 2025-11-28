package com.example.todo.domain.model

/**
 * 태스크의 통계 정보를 나타내는 데이터 클래스입니다.
 *
 * @property activeTasksPercent 활성(미완료) 태스크의 백분율 (0.0 ~ 100.0)
 * @property completedTasksPercent 완료된 태스크의 백분율 (0.0 ~ 100.0)
 * @property activeTasksCount 활성 태스크의 개수
 * @property completedTasksCount 완료된 태스크의 개수
 * @property isEmpty 등록된 태스크가 하나도 없는지 여부
 */
data class Statistics(
    val activeTasksPercent: Float = 0f,
    val completedTasksPercent: Float = 0f,
    val activeTasksCount: Int = 0,
    val completedTasksCount: Int = 0,
    val isEmpty: Boolean = true
)
