# `TasksViewModel` 리팩토링 명세서

## 개요 (Overview)
이 문서는 `TasksViewModel`을 최신 Android 아키텍처 패턴에 맞게 점진적으로 리팩토링하는 계획을 정의합니다. `LiveData` 기반의 기존 구현을 코루틴의 `StateFlow`, `SharedFlow`, `Flow` 연산자를 사용하는 리액티브(reactive) 모델로 전환하여 코드의 가독성, 안정성, 테스트 용이성을 높이는 것을 목표로 합니다.

리팩토링은 여러 단계로 나뉘어 진행되며, 각 단계는 구현 내용과 함께 상세한 테스트 계획을 포함하여 변경 사항을 안전하게 검증합니다.

---

## 리팩토링 실행 계획 (Phased Refactoring Plan)

### 1단계: UI 상태 캡슐화 (`StateFlow` 도입)

#### 목표 (Objective)
ViewModel이 UI 상태를 나타내기 위해 사용하는 여러 `LiveData`들을 단일 `TasksUiState` 데이터 클래스와 `StateFlow`로 통합하여 상태 관리를 중앙화합니다.

#### 테스트 계획 (Test Plan)
1.  **테스트 환경 설정**: `kotlinx-coroutines-test`의 `MainCoroutineRule`을 사용하여 ViewModel의 `viewModelScope`가 테스트 디스패처를 사용하도록 설정합니다.
2.  **테스트 코드 수정**: 기존 `LiveData` 값을 검증하던 테스트들을 `uiState.value`의 프로퍼티가 예상대로 변경되었는지 확인하도록 수정합니다.
3.  **검증 항목**: `setFiltering()` 호출 시 `uiState.value.filteringLabel`이 변경되는지, `loadTasks(true)` 호출 시 `uiState.value.isLoading` 상태가 `true`에서 `false`로 바뀌는지 등을 검증합니다.

#### 구현 작업 (Implementation Tasks)
1.  `TasksUiState` 데이터 클래스를 정의합니다.
    ```kotlin
    data class TasksUiState(
        val items: List<Task> = emptyList(),
        val isLoading: Boolean = false,
        @StringRes val filteringLabel: Int = R.string.label_all,
        @StringRes val noTasksLabel: Int = R.string.no_tasks_all,
        @DrawableRes val noTaskIconRes: Int = R.drawable.logo_no_fill,
        val addTaskVisible: Boolean = true
    )
    ```
2.  `TasksViewModel`에 `private val _uiState = MutableStateFlow(TasksUiState())`를 추가하고 `StateFlow`로 외부에 노출합니다.
3.  기존 `LiveData`를 업데이트하던 로직이 `_uiState`를 업데이트하도록 수정합니다.
4.  `TasksFragment`에서 `uiState` `StateFlow`를 라이프사이클에 맞게 구독하도록 변경합니다.
5.  필요시 `tasks_frag.xml`의 데이터 바인딩 표현식을 수정합니다.

#### 영향 분석 (Impact Analysis)
ViewModel과 View 사이의 계약(contract)만 변경됩니다. 핵심 데이터 로딩 로직은 그대로 유지되므로 비교적 안전한 첫 단계입니다.

---

### 2단계: 일회성 이벤트를 `SharedFlow`로 이전

#### 목표 (Objective)
`Event` 래퍼와 `LiveData`를 사용하던 일회성 이벤트(화면 이동, 스낵바) 처리 방식을 `SharedFlow`로 대체하여 보일러플레이트를 제거하고 코드를 간소화합니다.

#### 테스트 계획 (Test Plan)
1.  **라이브러리 추가**: 테스트 코드에서 `SharedFlow`를 안정적으로 검증하기 위해 `app.cash.turbine:turbine` 라이브러리를 추가합니다.
2.  **테스트 코드 작성**: `Turbine`의 `test` 블록을 사용하여 `SharedFlow`에서 방출되는 이벤트를 검증합니다.
3.  **검증 항목**:
    *   `openTask()` 호출 시 `openTaskEvent.test { awaitItem() }` 블록에서 올바른 태스크 ID가 방출되는지 확인합니다.
    *   `completeTask()` 호출 시 `userMessage.test { awaitItem() }` 블록에서 올바른 메시지 ID가 방출되는지 확인합니다.

#### 구현 작업 (Implementation Tasks)
1.  `TasksViewModel`의 `openTaskEvent`와 `snackbarText` `LiveData`를 `MutableSharedFlow`와 `SharedFlow`로 교체합니다.
    ```kotlin
    private val _userMessage = MutableSharedFlow<Int>()
    val userMessage: SharedFlow<Int> = _userMessage.asSharedFlow()

    private val _openTaskEvent = MutableSharedFlow<String>()
    val openTaskEvent: SharedFlow<String> = _openTaskEvent.asSharedFlow()
    ```
2.  이벤트를 발생시키는 로직에서 `_sharedFlow.emit()`을 호출하도록 수정합니다.
3.  `TasksFragment`에서 `SharedFlow`를 구독하여 이벤트를 처리하도록 수정합니다.

#### 영향 분석 (Impact Analysis)
이벤트 처리 메커니즘만 국소적으로 변경됩니다. 이벤트가 UI에서 정확히 한 번만 소비되는지 확인하는 것이 중요합니다.

---

### 3단계: `Flow` 연산자를 이용한 데이터 로딩 리팩토링

#### 목표 (Objective)
`combine`과 `catch` 같은 선언적 `Flow` 연산자를 사용하여 데이터 로딩/필터링 및 에러 처리 로직을 리액티브 스트림 방식으로 통합하고 간결하게 만듭니다.

#### 테스트 계획 (Test Plan)
1.  **Fake Repository 준비**: 테스트 중 데이터 스트림(성공, 에러 등)을 제어할 수 있는 `Flow` 기반의 `FakeTasksRepository`를 사용합니다.
2.  **검증 항목**:
    *   **초기 로딩**: ViewModel 생성 시 리포지토리의 초기 데이터와 필터가 `combine`되어 `uiState`가 올바르게 설정되는지 검증합니다.
    *   **필터링**: `setFiltering()` 호출 시 `uiState`의 `items`가 즉시 필터링된 결과로 업데이트되는지 검증합니다.
    *   **데이터 변경 감지**: `FakeTasksRepository`가 새 데이터를 방출하면 `uiState`가 자동으로 업데이트되는지 검증합니다.
    *   **에러 처리**: 리포지토리 `Flow`에서 예외 발생 시 `uiState`가 에러 상태를 반영하고, `userMessage`로 에러 메시지가 전달되는지 검증합니다.

#### 구현 작업 (Implementation Tasks)
1.  `TasksRepository` 인터페이스의 `observeTasks()`가 `Flow<Result<List<Task>>>`를 반환하도록 수정합니다.
2.  `TasksViewModel`에서 `_forceUpdate` 및 `switchMap` 로직을 제거합니다.
3.  `tasksRepository.observeTasks()`와 `savedStateHandle.getStateFlow()`를 `combine`하고, `catch`로 예외를 처리하는 새로운 데이터 스트림 로직을 구현합니다.
    ```kotlin
    // ViewModel 초기화 블록
    viewModelScope.launch {
        val filterTypeFlow = savedStateHandle.getStateFlow(...)
        combine(tasksRepository.observeTasks(), filterTypeFlow) { ... }
            .catch { error -> ... }
            .collect { state -> _uiState.value = state }
    }
    ```

#### 영향 분석 (Impact Analysis)
가장 복잡하고 핵심적인 변경입니다. 데이터 처리 방식이 근본적으로 바뀌므로 회귀 버그 발생 가능성이 가장 높습니다. `TasksRepository`의 시그니처 변경은 다른 코드에 영향을 줄 수 있습니다.

---

### 4단계: 통합 테스트(Instrumented Test) 검증

#### 목표 (Objective)
리팩토링된 모든 기능이 실제 기기/에뮬레이터 환경에서 사용자의 관점에서 올바르게 동작하는지 최종 검증합니다.

#### 테스트 계획 (Test Plan)
1.  **기존 테스트 실행**: `TasksActivityTest`와 같은 기존 통합 테스트를 실행하여 모든 사용자 시나리오가 정상 동작하는지 확인합니다.
2.  **Idling Resource 검증**: 코루틴/Flow 기반의 비동기 작업이 Espresso의 `IdlingResource`에 의해 올바르게 추적되는지 확인합니다. 테스트가 비동기 작업을 기다리지 못하고 실패한다면, `Flow` 수집 로직 주변에 `IdlingResource`의 카운터를 조절하는 코드를 추가해야 할 수 있습니다.

#### 구현 작업 (Implementation Tasks)
*   통합 테스트 실패 시, 비동기 작업 타이밍 이슈나 `IdlingResource` 설정을 수정하여 테스트가 안정적으로 통과하도록 만듭니다.

#### 영향 분석 (Impact Analysis)
이전 단계들에서 발생했을 수 있는 의도치 않은 부작용을 최종적으로 감지하고 수정하여 리팩토링의 안정성을 확보합니다.