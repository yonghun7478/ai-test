제안해주신 이슈 내용에 따라 **Senior Android Architect** 관점에서 `StatisticsViewModel`의 리팩토링 명세서를 작성했습니다.

이 명세서는 **Clean Architecture** 원칙을 도입하고, **Unidirectional Data Flow (UDF)** 패턴을 적용하여 코드의 유지보수성과 테스트 용이성을 높이는 것을 목표로 합니다.

---

# Specification: Refactor StatisticsViewModel

### Objective
현재 비대해지고 복잡도가 높은 `StatisticsViewModel`을 리팩토링하여 관심사를 분리(Separation of Concerns)하고 테스트 가능성을 확보한다.
비즈니스 로직을 `Domain Layer(UseCase)`로 이관하고, UI 상태 관리를 `StateFlow` 기반의 단일 상태(UiState)로 통합하여 데이터 흐름을 명확하게 개선한다.

### User Story
- **Developer**: 나는 통계 화면의 비즈니스 로직이 ViewModel에서 분리되어 `UseCase`로 관리되기를 원한다. 이를 통해 로직을 재사용하고 단위 테스트를 쉽게 작성할 수 있다.
- **Developer**: 나는 UI 상태가 산재된 `LiveData`/`State` 변수가 아닌 하나의 `UiState` 객체로 관리되기를 원한다. 이를 통해 UI 일관성을 유지하고 레이스 컨디션을 방지할 수 있다.

### Acceptance Criteria
- [ ] **Repository 직접 의존 제거**: ViewModel이 Repository를 직접 호출하지 않고, `UseCase`를 통해 데이터를 요청해야 한다.
- [ ] **비즈니스 로직 이관**: 통계 데이터를 가공, 필터링, 계산하는 로직은 `UseCase` 내부 혹은 도메인 모델 내부로 이동해야 한다.
- [ ] **단일 진실 공급원 (SSOT) 적용**: `MutableStateFlow<StatisticsUiState>`를 사용하여 UI 상태를 관리하고, 외부에 불변(Immutable) `StateFlow`로 노출해야 한다.
- [ ] **Coroutine Dispatcher 주입**: 하드코딩된 Dispatcher 사용을 금지하고, 테스트 가능하도록 주입받거나 표준 패턴을 사용해야 한다.
- [ ] **Unit Test 작성**: 리팩토링된 ViewModel과 새로 생성된 UseCase에 대한 단위 테스트가 100% 커버리지를 목표로 작성되어야 한다.

### Technical Details

#### 1. Architecture Change
- **Before**: `View` <-> `ViewModel` (Business Logic + State) <-> `Repository`
- **After**: `View` <-> `ViewModel` (State Holder) <-> `UseCase` (Business Logic) <-> `Repository`

#### 2. Files & Components

**A. Domain Layer (New/Extracted)**
- `GetWeeklyStatisticsUseCase.kt`: 주간 통계 데이터를 가져오고 가공하는 로직 캡슐화.
- `GetMonthlyStatisticsUseCase.kt`: 월간 통계 데이터를 가져오고 가공하는 로직 캡슐화.
- `StatisticsModel.kt`: UI에 최적화된 도메인 데이터 모델 (Entity와 분리).

**B. Presentation Layer (Refactor)**
- `StatisticsViewModel.kt`:
    - `Hilt` 또는 `Koin`을 통해 UseCase 주입.
    - `StatisticsUiState` (Sealed Interface/Data Class) 정의.
    - `StatisticsUiIntent` (Sealed Interface) 정의 (사용자 액션 처리).
- `StatisticsUiState.kt`:
    ```kotlin
    sealed interface StatisticsUiState {
        object Loading : StatisticsUiState
        data class Success(
            val weeklyData: List<ChartData>,
            val monthlyData: List<ChartData>,
            val totalSummary: SummaryData
        ) : StatisticsUiState
        data class Error(val message: String) : StatisticsUiState
    }
    ```

**C. Test**
- `StatisticsViewModelTest.kt`: Mock UseCase를 활용한 상태 검증.
- `GetWeeklyStatisticsUseCaseTest.kt`: 순수 비즈니스 로직 검증.

#### 3. Data Flow Strategy
- ViewModel은 `viewModelScope`에서 코루틴을 실행하여 UseCase를 호출한다.
- `Result<T>` 또는 `Flow<T>` 패턴을 사용하여 UseCase의 결과를 수신한다.
- `WhileSubscribed(5000)`을 적용하여 화면 회전 시 불필요한 재요청을 방지한다.

### Implementation Plan

1.  **UseCase 정의 및 구현**
    - 기존 ViewModel에 있는 통계 계산 로직(평균, 합계, 필터링 등)을 분석한다.
    - 이를 `GetWeeklyStatisticsUseCase`, `GetMonthlyStatisticsUseCase` 등으로 추출 및 구현한다.
    - 각 UseCase에 대한 Unit Test를 먼저 작성한다 (TDD 권장).

2.  **UI State & Intent 모델링**
    - 화면에 필요한 모든 데이터를 포함하는 `StatisticsUiState` 데이터 클래스를 정의한다.
    - 사용자의 입력(기간 변경, 탭 전환 등)을 정의하는 `StatisticsUiIntent`를 정의한다.

3.  **ViewModel 리팩토링**
    - 기존의 개별 `LiveData`/`State` 변수들을 제거한다.
    - `_uiState` (MutableStateFlow)와 `uiState` (StateFlow)를 선언한다.
    - 생성자에 UseCase를 주입받도록 변경한다.
    - 로직을 UseCase 호출 및 결과에 따른 `_uiState.update { ... }` 형태로 변경한다.

4.  **UI 연동 수정 (Fragment/Activity/Composable)**
    - 기존의 여러 Observer를 제거하고 `lifecycleScope.launch { viewModel.uiState.collect { ... } }` 형태로 변경한다.
    - 사용자 액션을 ViewModel의 메서드 호출 대신 `viewModel.handleIntent(Intent)` (또는 이에 준하는 함수) 형태로 변경한다.

5.  **최종 테스트 및 검증**
    - `StatisticsViewModelTest`를 작성하여 각 상태(Loading, Success, Error) 전이가 올바른지 검증한다.
    - 앱을 실행하여 기존 기능과 동일하게 동작하는지(Regression Test) 확인한다.