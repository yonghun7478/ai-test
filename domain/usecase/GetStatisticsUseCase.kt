package com.example.todo.domain.usecase

import com.example.todo.domain.model.Statistics
import com.example.todo.domain.model.Task
import com.example.todo.domain.repository.TasksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 태스크 목록을 기반으로 통계(Statistics)를 계산하는 UseCase입니다.
 *
 * TasksRepository에서 태스크 목록 스트림(Flow)을 받아,
 * 각 상태 변화마다 새로운 통계 정보를 계산하여 방출합니다.
 *
 * @property tasksRepository 태스크 데이터를 제공하는 저장소
 */
class GetStatisticsUseCase @Inject constructor(
    private val tasksRepository: TasksRepository
) {

    /**
     * 통계 정보를 Flow 형태로 반환합니다.
     *
     * @return Statistics 객체를 포함하는 Flow
     */
    operator fun invoke(): Flow<Statistics> {
        // Epic #15에서 정의된 getTasksStream()을 사용한다고 가정합니다.
        // Task 모델에 isCompleted 속성이 있다고 가정합니다.
        return tasksRepository.getTasksStream().map { tasks ->
            calculateStatistics(tasks)
        }
    }

    /**
     * 태스크 리스트를 받아 Statistics 모델로 변환하는 내부 로직입니다.
     */
    private fun calculateStatistics(tasks: List<Task>): Statistics {
        if (tasks.isEmpty()) {
            return Statistics(
                activeTasksPercent = 0f,
                completedTasksPercent = 0f,
                activeTasksCount = 0,
                completedTasksCount = 0,
                isEmpty = true
            )
        }

        val totalTasks = tasks.size
        // isCompleted가 true면 완료, false면 활성(Active) 상태로 간주
        val completedTasks = tasks.count { it.isCompleted }
        val activeTasks = totalTasks - completedTasks

        return Statistics(
            activeTasksPercent = (activeTasks.toFloat() / totalTasks) * 100f,
            completedTasksPercent = (completedTasks.toFloat() / totalTasks) * 100f,
            activeTasksCount = activeTasks,
            completedTasksCount = completedTasks,
            isEmpty = false
        )
    }
}
