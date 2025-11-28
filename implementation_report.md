
    # Implementation Report for #16
    
    ## Context
    /implement #15 참고해서 철저하게 #16 구현해 !!
    
    ## AI Analysis
    이 작업은 **Epic #15(Task 도메인 및 데이터 레이어)**의 연장선으로, 완료된 태스크와 활성 태스크의 통계를 계산하는 도메인 로직을 구현하는 것입니다.

기존에 구현되어 있다고 가정하는 `Task` 모델과 `TasksRepository` 인터페이스를 활용하여, **통계 데이터 모델(`Statistics`)**과 **통계 계산 유즈케이스(`GetStatisticsUseCase`)**를 작성하고, 이에 대한 **단위 테스트**를 MockK를 사용하여 구현하겠습니다.

### 구현 요약
1.  **`domain/model/Statistics.kt`**: 활성/완료 태스크의 개수와 백분율을 담는 불변 데이터 클래스를 정의합니다.
2.  **`domain/usecase/GetStatisticsUseCase.kt`**: `TasksRepository`의 `Flow` 데이터를 구독하여 실시간으로 통계를 계산하고 `Statistics` 객체로 변환하여 방출합니다.
3.  **`doma... (truncated)
    