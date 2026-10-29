# Mini MES Codex Working Rules

## 1. Project Goal

이 프로젝트는 **Java/Spring 기반 Mini MES - 생산실적 및 LOT 추적 시스템**이다.

목표는 단순 CRUD 구현이 아니라 다음 역량을 실제 코드와 검증 기록으로 증명하는 것이다.

- 제조 업무 흐름 이해
- 비즈니스 규칙 설계
- DB 및 데이터 정합성 설계
- 상태 전이 및 트랜잭션 설계
- BOM / Routing 버전 보존
- LOT Traceability 구현
- AI 생성 코드 검증 및 수정
- Docker / Linux / Nginx 기반 배포 및 운영

AI는 분석과 구현을 보조한다.

**업무 정책, 데이터 구조, 트랜잭션, 상태 전이, 운영 정책의 최종 결정자는 개발자다.**

## 2. Technology Constraints

### Backend
- Java 17
- Spring Boot
- Spring MVC
- MyBatis
- Gradle

### Database
- MySQL

### Frontend
- Thymeleaf
- JavaScript

### Test
- JUnit 5
- Spring Boot Test

### Infra
- Docker
- Linux
- Nginx

사용자가 명시적으로 요청하지 않는 한 새로운 Framework 또는 Library를 추가하지 않는다.

특히 다음은 임의 도입 금지:
- JPA
- React / Vue
- MSA
- Kafka
- Redis

새 Library가 반드시 필요하다면 먼저 필요성과 대안을 설명하고 사용자 승인을 받는다.

## 3. Source of Truth

구현 시 모든 문서를 매번 전체 탐색하지 않는다.

**현재 작업과 직접 관련된 문서와 코드만 확인한다.**

우선순위:
1. 관련 Business Rule
2. 관련 DB 설계
3. 관련 Architecture Rule
4. 관련 PRD 항목
5. 기존 유사 구현

문서 간 충돌이 있거나 다음 표현이 있는 정책은 AI가 임의로 결정하지 않는다.

- 설계 결정 필요
- Business Decision Required
- 미확정
- 후속 결정 필요

이 경우 사용자에게 결정을 요청한다.

## 4. MES Business Rules

### General
- Controller에 Business Logic을 작성하지 않는다.
- Mapper SQL에 Business Policy를 숨기지 않는다.
- 화면 JavaScript에서 서버 핵심 업무 규칙을 대체하지 않는다.
- null 허용, 임의 기본값, 예외 무시로 업무 오류를 우회하지 않는다.
- Business Rule 위반 상태를 저장하지 않는다.
- 상태값은 명시된 전이 규칙을 따른다.
- 여러 데이터가 함께 변경되는 업무는 Transaction 정합성을 검토한다.
- 변경·취소·정정 시 관련 상태, 수량, 이력이 서로 모순되지 않아야 한다.

### Historical Data
- 기준정보 변경으로 과거 생산 이력이 바뀌어서는 안 된다.
- 과거 생산에 사용한 BOM Version을 보존한다.
- 과거 작업지시에 사용한 Routing을 확인할 수 있어야 한다.
- 현재 BOM으로 과거 원자재 사용 이력을 추정하지 않는다.
- 실제 생산 당시 저장된 연결 관계를 기준으로 이력을 조회한다.

### Production Quantity

기본 정합성 규칙:

```text
생산수량 = 양품수량 + 불량수량
```

수량 정책이 미확정인 경우 AI가 초과 생산, 음수 보정, 자동 절삭 등을 임의 적용하지 않는다.

### LOT
- LOT 번호 유일성 규칙을 임의로 변경하지 않는다.
- LOT 사용 이력은 삭제보다 보존을 우선한다.
- 원자재 LOT 사용과 작업지시의 연결을 보존한다.
- 완제품 LOT과 생산실적의 연결을 보존한다.
- LOT Traceability는 실제 저장된 이력을 기준으로 구현한다.

### Work Order
- 작업지시 상태 전이는 정의된 정책을 따른다.
- 완료, 취소, 수정 조건이 확정되지 않은 경우 임의 구현하지 않는다.
- 생산실적 또는 LOT 이력이 존재하는 작업지시 취소는 관련 영향 범위를 먼저 확인한다.
- 작업지시 생성 시 사용한 BOM / Routing 기준이 이후 변경으로 훼손되지 않도록 한다.

## 5. Architecture Rules

기본 구조는 **도메인별 모듈형 모놀리스**를 사용한다.

```text
com.taehyung.mes
├─ item
├─ bom
├─ process
├─ routing
├─ production
├─ workorder
├─ lot
├─ quality
├─ trace
└─ common
```

### Controller
- HTTP 요청/응답
- 요청값 변환
- 입력 검증 연결
- Service 호출

### Service
- Business Rule
- 상태 전이
- 업무 Flow
- Transaction 경계

### Mapper
- DB 조회 및 변경
- Persistence 책임

작은 프로젝트라는 이유로 모든 로직을 하나의 Service에 몰아넣지 않는다.
반대로 불필요하게 Clean Architecture, Hexagonal Architecture, DDD Framework 등을 도입하지 않는다.

## 6. Scope Control

- 요청한 기능만 수정한다.
- 요청 범위 밖 Refactoring을 하지 않는다.
- 관련 없는 파일을 탐색하지 않는다.
- 관련 없는 모듈 구조를 변경하지 않는다.
- 전체 Repository 분석은 사용자가 명시적으로 요청한 경우에만 수행한다.
- 기존 유사 구현이 있으면 필요한 범위에서만 확인하고 재사용한다.
- 광범위한 파일 이동이나 이름 변경을 하지 않는다.

작업 범위를 넓혀야 정상 해결이 가능한 경우 먼저 다음을 설명하고 승인받는다.

1. 이유
2. 영향 파일
3. 필요한 변경 범위

## 7. Usage Optimization

이 프로젝트에서는 Codex 사용량을 최소화한다.

사용자가 명시적으로 요청하지 않는 한 다음 작업을 실행하지 않는다.

- Browser 실행
- 사이트 렌더링
- Playwright
- Screenshot
- Viewport 테스트
- 개발 서버 실행
- `./gradlew bootRun`
- 전체 E2E 테스트
- Repository 전체 분석
- 모든 문서 전체 재검토
- 불필요한 외부 검색

작업과 직접 관련된 파일만 확인한다.

- 이미 확인한 프로젝트 구조, DB 스키마, 설정, 권한은 변경 근거가 없는 한 반복 조회하지 않는다.
- 단순 오류 수정은 오류가 발생한 파일과 직접 관련된 코드부터 확인한다.
- 빌드·테스트 로그는 실패 요약과 핵심 오류부터 확인하고, 필요한 경우에만 관련 상세 로그를 조회한다.
- 동일한 검증 명령을 이유 없이 반복 실행하지 않는다.
- 이미 생성한 테스트 DB와 계정은 매번 재생성하거나 전체 구조를 재검증하지 않는다. 접속 대상과 권한 등 필수 안전 확인은 유지한다.
- 독립 리뷰 에이전트는 명시적으로 요청받거나 위험도가 높은 변경에 필요한 경우에만 사용한다.
- 작업 계획과 진행 보고를 불필요하게 반복 작성하지 않는다.
- 작은 수정은 관련 파일 확인 → 최소 변경 → 승인된 필요한 테스트 → 결과 보고 순서로 수행한다.
- 기존 Graphify 사용 규칙을 유지하고, 관련 코드 탐색에 도움이 되는 경우에만 사용한다.
- 토큰 절약을 이유로 데이터 손상 방지, 트랜잭션 정합성, 보안 검증을 생략하지 않는다.

빌드, 테스트, 린트는 자동 실행하지 않는다.

검증 실행이 필요하다고 판단하면 먼저 다음을 알려준다.

1. 필요한 이유
2. 실행할 명령
3. 예상 검증 범위

사용자가 명시적으로 요청한 뒤 실행한다.

## 8. Code Modification Workflow

### 수정 전
1. 현재 요청
2. 관련 PRD 항목
3. 관련 Business Rule
4. 관련 DB 구조
5. 관련 기존 코드
6. Transaction 영향

전체 프로젝트 구조를 다시 분석하지 않는다.

### 수정 중
- 기존 Naming Convention 유지
- 기존 패키지 구조 유지
- Business Rule을 구현 편의 때문에 단순화하지 않음
- 미확정 업무 정책을 추측으로 구현하지 않음
- 임시 하드코딩으로 로직을 우회하지 않음
- null일 때 무조건 통과시키는 방식으로 오류를 숨기지 않음
- 예외를 catch 후 무시하지 않음
- 데이터 정합성 문제를 화면 검증만으로 해결하지 않음

### 수정 후
정적 검토 시 다음을 확인한다.

1. 요청 범위 충족
2. Business Rule 충족
3. 상태 전이 일관성
4. Transaction 영향
5. DB Constraint 충돌 여부
6. 과거 생산 이력 훼손 가능성
7. LOT Traceability 영향
8. 요청 범위 밖 변경 여부

## 9. Business Decision Required

다음과 같은 경우 AI가 임의 결정하지 않는다.

- 생산실적과 완제품 LOT 생성 시점이 불명확함
- 품질검사 대상 단위가 불명확함
- 원자재 예약 여부가 확정되지 않음
- 작업지시 취소 조건이 확정되지 않음
- 생산실적 정정 정책이 확정되지 않음
- 초과 생산 허용 여부가 확정되지 않음
- BOM / Routing 변경 정책이 확정되지 않음
- 품질 FAIL 이후 처리 방식이 확정되지 않음
- 삭제 또는 이력 보존 기준이 불명확함

보고 형식:

```text
Business Decision Required

문제:
[결정이 필요한 항목]

현재 확인 가능한 규칙:
[관련 PRD / Business Rule]

선택지:
1. ...
2. ...

영향:
[DB / Transaction / 화면 / 이력 영향]
```

사용자의 결정 전에는 해당 정책을 구현하지 않는다.

## 10. Database Rules

- PK / FK 관계를 명확히 유지한다.
- 코드 유일성은 가능한 경우 DB UNIQUE Constraint로도 보호한다.
- 애플리케이션 Validation만으로 동시성 정합성을 보장한다고 가정하지 않는다.
- 실제 이력 데이터에 필요한 기준정보를 단순 물리 삭제하지 않는다.
- 변경 이력이 필요한 업무는 기존 값을 덮어써서 과거 정보를 잃지 않도록 한다.
- Transaction이 필요한 다중 변경은 일부만 저장된 상태가 남지 않도록 한다.
- 동시 요청으로 LOT 수량 또는 생산수량이 중복 반영될 가능성을 검토한다.

DB Constraint와 Service 검증은 서로 대체 관계로 보지 않는다.

## 11. Testing and Verification

사용자가 테스트를 요청한 경우 Business Rule 중심으로 검증한다.

우선 검토 대상:
- 정상 상태 전이
- 허용되지 않은 상태 전이
- 중복 데이터
- 음수 / 0 / 초과 수량
- 생산수량 정합성
- 작업지시 취소
- 실적 정정
- BOM Version 보존
- LOT 중복
- LOT 수량 초과 사용
- Traceability
- Transaction Rollback
- 동시 요청 가능성이 있는 수량 변경

테스트 결과를 실제 실행하지 않았다면 실행한 것처럼 표현하지 않는다.

## 12. AI Development Record

AI가 잘못된 설계 또는 코드를 제안한 경우 숨기지 않는다.

필요한 경우 다음 위치에 기록한다.

```text
docs/ai/failures/
```

기록 형식:

```md
# AI Failure - [주제]

## AI Suggestion
AI가 제안한 내용

## Problem
왜 잘못되었는가

## Risk
어떤 데이터 또는 업무 문제가 발생하는가

## Verification
어떻게 문제를 확인했는가

## Fix
어떻게 수정했는가
```

AI 오류를 억지로 만들지 않는다.
실제로 개발 과정에서 발생한 사례만 기록한다.

## 13. Response Rules

작업 완료 후 장문의 분석 보고서를 작성하지 않는다.

다음만 짧게 보고한다.

1. 수정한 파일
2. 적용한 Business Rule
3. 핵심 변경사항
4. 실행하지 않은 검증
5. 사용자가 직접 검토하거나 결정해야 할 항목

## 14. Priority

모든 작업의 우선순위는 다음과 같다.

```text
Business Rule
>
Data Integrity
>
Historical Traceability
>
Maintainability
>
Implementation Convenience
```

구현을 단순하게 만들기 위해 Business Rule이나 데이터 정합성을 희생하지 않는다.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- Use Graphify first when relevant code exploration or dependency analysis is needed; do not invoke it for a simple edit to files already identified. Before a graph-based lookup, use changed-file information already established in the current task to assess freshness. Run `graphify update .` before `graphify query "<question>"` only when there is evidence that relevant code is newer than the graph. Include this rule in subagent prompts that explore code. For graph-based exploration, read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates and are not a reason to skip a Graphify lookup needed for code exploration or dependency analysis.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- Treat `graphify update .` as a tool operation that does not change source code. If an existing approval rule explicitly requires approval for that operation, obtain it before running the update.
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
