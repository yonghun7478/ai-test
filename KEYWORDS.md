# Project Keywords & Core Principles

이 문서는 `architecture-samples` 프로젝트에서 사용되는 핵심 키워드, 아키텍처 원칙, 그리고 Agent 행동 수칙을 요약한 것입니다. 다른 프로젝트의 AI Agent 설정(Prompt Engineering) 시 기초 자료로 활용할 수 있습니다.

## 1. Agent Persona & Core Behaviors (행동 수칙)

*   **Role:** Senior Android Architect (단순 코더가 아닌 아키텍처 설계자)
*   **Context-Aware:** 작업 전 `codebase_investigator`, `read_file`을 통한 맥락 파악 필수.
*   **One Step at a Time:** 한 번에 하나의 작업 단위만 수행하고 사용자 승인 대기.
*   **Defensive Coding:** 기존 컨벤션 및 스타일 엄수, 사이드 이펙트 고려.
*   **Active Knowledge Retrieval:** (New) 과거 학습 데이터 의존 지양, 최신 공식 문서 검색 생활화.

## 2. Development Methodology (개발 방법론)

*   **SDD (Specification-Driven Development):** 코드 작성 전 명세서(Spec) 작성 -> 분석 -> 계획 수립 -> 구현 순서 준수.
*   **TDD (Test-Driven Development):** 실패하는 테스트 작성 -> 구현 -> 테스트 통과 -> 리팩토링.
*   **Review-Friendly:** 작업 단위를 잘게 쪼개어 리뷰 및 검증 용이성 확보.

## 3. Architecture & Design Patterns (아키텍처)

*   **Pattern:** MVVM (Model-View-ViewModel)
*   **Structure:** Single-Activity Architecture (Jetpack Navigation)
*   **Data Layer:** Repository Pattern (Single Source of Truth)
*   **Dependency Injection:** Service Locator / Dependency Injection (Manual or Dagger/Hilt)
*   **Separation of Concerns:** UI(View)와 비즈니스 로직(ViewModel), 데이터(Repository)의 명확한 분리.

## 4. Tech Stack & Libraries (기술 스택)

*   **Language:** Kotlin (Coroutines, Flow)
*   **UI:** Android View System (XML), Data Binding
*   **Jetpack Components:**
    *   Navigation Component
    *   Room (Local Database)
    *   ViewModel & LiveData
    *   LifecycleAware components
*   **Testing:** JUnit4, Espresso, Mockito, AndroidX Test

## 5. Code Quality (품질 관리)

*   **Linting:** ktlint, Spotless
*   **CI/CD:** GitHub Actions (Build, Test, Lint checks)
*   **Environments:** Product Flavors (Mock vs Prod)
