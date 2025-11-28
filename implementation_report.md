
    # Implementation Report for #17
    
    ## Context
    /implement 메인 이슈는 이거고#15,  과거에 작업한 #20 PR이야. #17을 작업을 철저하게 진행해 !
    
    ## AI Analysis
    이 작업은 통계 화면(Statistics Screen)의 UI 상태를 관리하기 위한 `StatisticsUiState` 데이터 클래스를 정의하는 것입니다.

**요약:**
1.  **UI 상태 정의**: `StatisticsUiState` 데이터 클래스를 생성하여 로딩, 통계 수치(활성/완료 백분율), 데이터 없음, 에러 상태를 캡슐화합니다.
2.  **초기 상태 제공**: ViewModel 등에서 초기값으로 즉시 사용할 수 있도록 `companion object` 내에 `Initial` 상태를 정의합니다.
3.  **단위 테스트**: 데이터 클래스가 의도대로 생성되고 수정(copy)되는지 확인하는 JVM 단위 테스트를 작성합니다.

---

### FILE: app/src/main/java/com/example/todo/statistics/StatisticsUiState.kt
```kotlin
package com.example.todo.statistics

/**
 * 통계 화면... (truncated)
    