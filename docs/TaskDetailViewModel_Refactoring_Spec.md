### Feature/Bug Name: Refactor TaskDetailViewModel

### Objective:
`TaskDetailViewModel`을 `LiveData`에서 `StateFlow` 및 `SharedFlow`로 마이그레이션하여 최신 안드로이드 아키텍처 패턴을 사용하도록 리팩토링합니다. 이를 통해 보다 선언적이고 반응적인 접근 방식을 채택하여 코드 품질, 테스트 용이성 및 유지보수성을 향상시킵니다.

### User Story / Scenario:
- **As a:** Developer
- **I want to:** `TaskDetailViewModel`을 리팩토링하고 싶습니다.
- **So that:** 코드베이스가 더 견고해지고 이해하기 쉬우며, 현재 안드로이드 개발 모범 사례에 부합하도록 만들 수 있습니다.

### Acceptance Criteria:
- [ ] `TaskDetailViewModel`은 더 이상 UI 상태(`task`, `isDataAvailable`, `dataLoading`)를 노출하기 위해 여러 `LiveData` 객체를 사용하지 않습니다.
- [ ] 단일 `StateFlow`가 모든 UI 관련 상태(작업 데이터, 로딩 상태, 오류 메시지 등)를 캡슐화하는 `TaskDetailUiState` 데이터 클래스를 노출합니다.
- [ ] 내비게이션 및 스낵바 표시와 같은 일회성 이벤트는 `LiveData`와 함께 `Event` 래퍼 클래스 대신 `SharedFlow`로 처리됩니다.
- [ ] 데이터 로딩은 `taskId`를 기반으로 선언적으로 트리거되며, 가급적 `flatMapLatest`와 같은 `Flow` 연산자를 사용하고 `stateIn`을 사용하여 `StateFlow`로 변환됩니다.
- [ ] `TaskDetailFragment`는 새로운 `StateFlow` 및 `SharedFlow`에서 수집하여 UI를 업데이트하도록 수정됩니다.
- [ ] 기존의 모든 기능(작업 보기, 완료, 삭제, 편집)은 사용자 관점에서 동일하게 유지됩니다.
- [ ] 관련된 모든 단위 테스트(`TaskDetailViewModelTest`) 및 계측 테스트가 새 구현에 맞게 업데이트되어 통과합니다.

### Technical Details / Constraints:
- **Affected Files:**
    - `app/src/main/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailViewModel.kt`
    - `app/src/main/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailFragment.kt`
    - `app/src/test/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailViewModelTest.kt`
- **Implementation Notes:**
    - 화면 상태를 담을 `TaskDetailUiState` 데이터 클래스를 정의합니다.
    - 내부 상태에는 `MutableStateFlow`를 사용하고 외부에는 `StateFlow`로 노출합니다.
    - 이벤트 처리를 위해 `MutableSharedFlow`를 사용합니다.
    - `TasksRepository`는 `Flow` 객체를 반환하는 것이 이상적입니다. 현재 구현을 확인하고 필요에 따라 조정해야 합니다.
- **Things to Avoid:**
    - 동일한 ViewModel 내에서 상태 관리를 위해 `LiveData`와 `Flow`를 혼용하는 것.
    - `MutableStateFlow` 또는 `MutableSharedFlow`를 Fragment에 직접 노출하는 것.

### 심층 영향 분석 및 대처 방안 (Deep Impact Analysis & Mitigation):

- **영향을 받는 파일 목록 (Affected Files):**
    - `app/src/main/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailViewModel.kt` (리팩토링 대상)
    - `app/src/main/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailFragment.kt` (UI 로직 수정)
    - `app/src/main/res/layout/taskdetail_frag.xml` (데이터 바인딩 제거)
    - `app/src/test/java/com/example/android/architecture/blueprints/todoapp/taskdetail/TaskDetailViewModelTest.kt` (테스트 재작성)
    - `app/build.gradle` (테스트 라이브러리 의존성 추가)
    - `app/src/main/java/com/example/android/architecture/blueprints/todoapp/data/source/TasksRepository.kt` (참조용, 수정 불필요)

- **파급 효과 및 리스크 (Ripple Effects & Risks):**
    - **Data Binding:** `taskdetail_frag.xml`은 ViewModel의 `LiveData` 객체에 직접 바인딩되어 있습니다. `StateFlow`로 마이그레이션하면 기존 데이터 바인딩 코드가 손상되어 빌드 에러를 유발합니다.
    - **UI 업데이트 로직:** Fragment의 `LiveData` Observer들이 모두 제거되고, `StateFlow`를 수집(collect)하여 UI를 업데이트하는 새로운 로직으로 대체되어야 합니다. 이 과정에서 UI 상태(로딩, 데이터 표시, 에러 등)가 올바르게 처리되지 않을 리스크가 있습니다.
    - **테스트 코드:** `LiveData` 테스트에 사용되던 `InstantTaskExecutorRule`과 `getOrAwaitValue()` 같은 유틸리티는 더 이상 유효하지 않습니다. `StateFlow`와 `SharedFlow`를 테스트하기 위한 새로운 라이브러리와 접근 방식이 필요하며, 기존의 모든 테스트 케이스를 재작성해야 합니다.

- **구체적인 대처 방안 (Mitigation Strategy):**
    - **Data Binding 제거:** `taskdetail_frag.xml`에서 데이터 바인딩 표현식을 모두 제거합니다. 대신 `TaskDetailFragment` 내에서 `StateFlow<UiState>`를 구독하고, 그 결과를 바탕으로 View(TextView, CheckBox 등)의 속성을 직접 프로그래밍 방식으로 설정합니다. 이는 View와 ViewModel 간의 결합도를 낮추는 더 나은 아키텍처입니다.
    - **테스트 라이브러리 도입:** `Flow` 테스트를 위해 표준 라이브러리인 `app.cash.turbine:turbine` (버전 1.2.1 확인)을 도입합니다. `app/build.gradle`의 `testImplementation`에 의존성을 추가합니다.
    - **테스트 재작성:** `TaskDetailViewModelTest`에서 `InstantTaskExecutorRule`을 제거하고, `runTest`와 Turbine의 `test` 함수를 사용하여 각 테스트 케이스를 `Flow` 기반으로 재작성합니다. `UiState`의 변화를 시간 순서대로 `awaitItem()`을 통해 검증합니다.

- **작업 분할 계획 (Sub-task Plan):**
    - [ ] 1. `app/build.gradle`에 `turbine` 테스트 의존성 추가.
    - [ ] 2. **(동시 진행)** `TaskDetailViewModel`을 `StateFlow`로 리팩토링하고, `TaskDetailViewModelTest`를 `Turbine`을 사용해 새로운 API에 맞게 재작성.
    - [ ] 3. `taskdetail_frag.xml`에서 모든 데이터 바인딩 표현식 제거.
    - [ ] 4. `TaskDetailFragment`를 `Flow`를 수집하도록 리팩토링.
    - [ ] 5. 모든 테스트 (`./gradlew test`, `./gradlew connectedCheck`)를 실행하여 기능이 정상 동작하고 회귀(regression)가 없는지 최종 검증.
