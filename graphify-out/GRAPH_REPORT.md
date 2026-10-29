# Graph Report - mes_side_project  (2026-10-29)

## Corpus Check
- 219 files · ~99,456 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 25 file(s) not represented in the graph (top: .xml 12, (none) 6, .properties 2)

## Summary
- 2863 nodes · 7560 edges · 167 communities (69 shown, 98 thin omitted)
- Extraction: 77% EXTRACTED · 23% INFERRED · 0% AMBIGUOUS · INFERRED: 1715 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `c04247f6`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- RawMaterialLot
- 공정 관리
- Item
- ProductionPlan
- 웹 예외 처리
- 애플리케이션 테스트
- 작업지시 도메인
- 작업지시 입력 옵션
- WorkOrderStatus
- 작업지시 Mapper 연계
- 원자재 소비
- BOM 버전
- DB 도메인 설계
- BOM 서비스 테스트
- org.junit.jupiter.api.Test
- BOM 서비스
- 제품 요구사항
- .create
- BOM Mapper
- 소비 통합 테스트
- list
- Routing 개정
- Spring 설정
- WorkOrderTotals
- MaterialConsumptionMapper
- MaterialConsumptionMaterial 관계
- DashboardSummary
- Mini MES Business Rules 관계
- RoutingService 관계
- ProductionResult
- WorkOrderMysqlIntegrationTests 관계
- BomComponent
- WorkOrderRuleException
- mysql 관계
- BOM 관리 구현 계획 관계
- 4. 테이블별 컬럼 명세 관계
- org.springframework.ui.Model
- RoutingServiceTests 관계
- Routing 관계
- RoutingController 관계
- RoutingMysqlIntegrationTests 관계
- ItemController 관계
- MaterialConsumptionReceipt 관계
- 작업지시 상세·상태 전이 화면 관계
- MaterialReturn
- WorkOrderRoutingOption 관계
- MaterialConsumptionForm 관계
- ProductionResultService
- MaterialConsumptionOutcome 관계
- BomController 관계
- MaterialConsumptionCommand 관계
- WorkOrderController 관계
- Mini MES Codex Working 관계
- RawMaterialLotController 관계
- WorkOrderTransitionForm 관계
- MaterialConsumptionController 관계
- 11. 업무 의사결정 현황 관계
- Mini MES 공통 화면 관계
- ProductionPlanController 관계
- application-dev.yml 관계
- Mini MES MySQL 물리 관계
- 원자재 투입 및 실제 관계
- Routing 구현 및 검증 관계
- 생산계획 구현 및 검증 관계
- Routing 구현 및 검증 관계
- 작업지시 구현 및 검증 관계
- ProductionPlanMysqlIntegrationTests
- Parent Lock and Conditional 관계
- AI Failure - MySQL 관계
- AI Failure - 작업지시 관계
- BOM 관리 구현 및 관계
- 공정 관리 구현 및 관계
- 원자재 LOT 등록·조회 구현 관계
- QualityInspection
- 2. Technology Constraints 관계
- 4. MES Business Rules 관계
- 생산계획 구현 및 검증 관계
- 10. 동시성 및 중복 관계
- BR-06. 품목 기준정보 및 관계
- BR-11. 작업지시 등록·조회·시작·시작 전 관계
- MesApplication 관계
- 5. Architecture Rules 관계
- 8. Code Modification Workflow 관계
- Actual History Trace Path 관계
- BR-07. BOM 관리 관계
- 14. 미확정 사항과 구현 관계
- 2. 사용 DB 및 관계
- 5. PK/FK/UNIQUE/CHECK와 보장 범위 관계
- 7. 상태·코드·버전 및 이력 관계
- 9. 업무별 트랜잭션 관계
- 6. 생산실적 및 완제품 관계
- 7. 품질검사 및 불량 관계
- Inspection Target Is Completed 관계
- 13. 설계 선택 근거 관계
- 8. 수량 저장·집계 전략 관계
- MaterialConsumptionRequestExistsException 관계
- Finished Goods LOT Created 관계
- Production Quantity Invariant 관계
- Raw Material LOT Global 관계
- 품목 코드·구분·단위 불변성 관계
- 2026-10-27-material-consumption.md 관계
- 2026-10-27-raw-material-lot.md 관계
- 품목 등록·수정 화면 관계
- 공정 등록·수정 화면 관계
- Eighteen Core Tables 관계
- Mini MES Backend Setup 관계
- 요청 오류 안내 화면 관계
- 공통 화면 레이아웃 관계
- ProductionResultCorrection
- .setUp
- MaterialReturnMysqlIntegrationTests
- ProductionResultForm
- QualityInspectionLot
- FinishedGoodsLot
- LotTraceDetails
- ProductionResultMysqlIntegrationTests
- QualityInspectionMapper
- .create
- ProductionResultMapper
- QualityInspectionForm
- RawMaterialLotMysqlIntegrationTests
- MaterialReturnForm
- MaterialReturnReceipt
- .create
- ProductionResultReceipt
- QualityInspectionReceipt
- .form
- MaterialReturnService
- ProductionPlanService
- ProductionResultCorrectionForm
- QualityInspectionController
- .create
- ItemKind
- ProductionPlanServiceTests
- DashboardMaterialLots
- RawMaterialLotMapper
- MaterialReturnController
- .get
- DashboardController
- ItemMapper
- DashboardMapper
- RawMaterialLotServiceTests
- 테스트 구분
- .get
- 9차 원자재 반환 구현·검증 기록
- ItemUnit
- Material Return Implementation Plan
- 원자재 반환 설계
- MaterialConsumptionOptions
- .create
- ProductionPlanControllerTests
- MaterialReturnMapperTests
- MaterialReturnRequestExistsException
- ProductionResultRequestExistsException
- QualityInspectionRequestExistsException

## God Nodes (most connected - your core abstractions)
1. `WorkOrder` - 88 edges
2. `Item` - 73 edges
3. `MaterialConsumption` - 66 edges
4. `ProductionResult` - 65 edges
5. `MaterialReturn` - 58 edges
6. `WorkOrderMapper` - 54 edges
7. `QualityInspection` - 53 edges
8. `BomVersion` - 49 edges
9. `DashboardSummary` - 47 edges
10. `RawMaterialLot` - 46 edges

## Surprising Connections (you probably didn't know these)
- `향후 실행과 검증` --references--> `MesApplicationTests`  [INFERRED]
  README.md → src/test/java/com/taehyung/mes/MesApplicationTests.java
- `테스트 구분` --references--> `ProductionResultContractTests`  [INFERRED]
  docs/production-result-verification.md → src/test/java/com/taehyung/mes/production/ProductionResultContractTests.java
- `테스트 구분` --references--> `ProductionResultControllerTests`  [INFERRED]
  docs/production-result-verification.md → src/test/java/com/taehyung/mes/production/ProductionResultControllerTests.java
- `테스트 구분` --references--> `ProductionResultMysqlIntegrationTests`  [INFERRED]
  docs/production-result-verification.md → src/test/java/com/taehyung/mes/production/ProductionResultMysqlIntegrationTests.java
- `테스트 구분` --references--> `ProductionResultServiceTests`  [INFERRED]
  docs/production-result-verification.md → src/test/java/com/taehyung/mes/production/ProductionResultServiceTests.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **원자재 소비 추적 흐름** — src_main_resources_templates_lot_consumption_detail_template, src_main_resources_templates_lot_raw_detail_template, src_main_resources_templates_workorder_detail_template [EXTRACTED 1.00]
- **작업지시에 확정된 생산 기준** — src_main_resources_templates_workorder_detail_template, src_main_resources_templates_bom_detail_template, src_main_resources_templates_routing_detail_template [EXTRACTED 1.00]
- **Applied Version Traceability** — agents_historical_traceability, docs_business_rules_bom_routing_version_preservation, docs_database_design_immutable_shared_versions, docs_erd_design_actual_history_trace_path [INFERRED 0.85]
- **과거 생산 기준정보와 소비 이력 보존** — docs_bom_verification_bom_version_immutability, docs_routing_verification_revision_snapshot, docs_work_order_verification_work_order_historical_versions, docs_material_consumption_verification_historical_consumption [INFERRED 0.85]

## Communities (167 total, 98 thin omitted)

### Community 1 - "공정 관리"
Cohesion: 0.06
Nodes (8): ManufacturingProcess, ProcessController, ProcessForm, ProcessMapper, ProcessRuleException, ProcessService, ProcessControllerTests, ProcessServiceTests

### Community 4 - "웹 예외 처리"
Cohesion: 0.07
Nodes (10): BomNotFoundException, PageExceptionHandler, ItemNotFoundException, MaterialConsumptionNotFoundException, MaterialReturnNotFoundException, RawMaterialLotNotFoundException, ProcessNotFoundException, ProductionPlanNotFoundException (+2 more)

### Community 5 - "애플리케이션 테스트"
Cohesion: 0.07
Nodes (11): mini_mes, MySQL 개발 환경, 개발 도구와 버전, 스키마 단일 원본과 적용 전 확인, 최소 디렉터리 구조, 향후 실행과 검증, 환경변수 설정, KstDateTimeFormatter (+3 more)

### Community 7 - "작업지시 입력 옵션"
Cohesion: 0.07
Nodes (3): WorkOrderAllocation, WorkOrderBomOption, WorkOrderOptions

### Community 8 - "WorkOrderStatus"
Cohesion: 0.13
Nodes (5): WorkOrderStatus, CANCELLED, COMPLETED, IN_PROGRESS, READY

### Community 10 - "원자재 소비"
Cohesion: 0.06
Nodes (3): MaterialConsumption, MaterialLotTotal, MaterialUsage

### Community 12 - "DB 도메인 설계"
Cohesion: 0.05
Nodes (36): 10. 생산실적 정정 이력 모델, 11.1 B 추천: 업무 요청 전용 RequestReceipt, 11.2 원자 처리와 결과 연결, 11.3 8차 원자재 실제 소비 확정, 11. 중복 요청 방지 모델, 12. 트랜잭션 경계, 13.1 불변식별 제어 수단, 13.2 잠금 순서와 최신 집계 (+28 more)

### Community 14 - "org.junit.jupiter.api.Test"
Cohesion: 0.12
Nodes (7): MaterialConsumptionMapperTests, MaterialReturnContractTests, MaterialReturnServiceTests, ProcessMapperTests, ProductionPlanMapperTests, RoutingMapperTests, WorkOrderMapperTests

### Community 15 - "BOM 서비스"
Cohesion: 0.11
Nodes (4): BomForm, BomRuleException, BomService, BomControllerTests

### Community 16 - "제품 요구사항"
Cohesion: 0.06
Nodes (32): 10. 우선 결정할 업무 정책, 11. AI 활용 및 검증 원칙, 12. 후속 작업, 1. 프로젝트 목적, 2.1 MVP 포함, 2.2 MVP 제외, 2.3 범위 확정 필요, 2. 범위 (+24 more)

### Community 17 - ".create"
Cohesion: 0.10
Nodes (4): WorkOrderForm, WorkOrderService, WorkOrderControllerTests, WorkOrderServiceTests

### Community 22 - "Spring 설정"
Cohesion: 0.07
Nodes (29): id, name, map-underscore-to-camel-case, driver-class-name, hikari, password, url, username (+21 more)

### Community 24 - "MaterialConsumptionMapper"
Cohesion: 0.11
Nodes (5): MaterialConsumptionMapper, MaterialConsumptionOutcomeCodec, MaterialConsumptionService, MaterialConsumptionTransactionService, MaterialConsumptionServiceTests

### Community 27 - "Mini MES Business Rules 관계"
Cohesion: 0.09
Nodes (24): 10. 업무 예외 시나리오, 12. 후속 설계 작업, 1. 문서 목적 및 적용 범위, 2. 기준 제조 업무 시나리오, 3. 핵심 도메인 관계, 4. 작업지시 상태 전이, 5. 원자재 LOT 및 수량 관리, 8. LOT 추적 규칙 (+16 more)

### Community 28 - "RoutingService 관계"
Cohesion: 0.15
Nodes (4): RoutingForm, RoutingRuleException, RoutingService, RoutingControllerTests

### Community 33 - "mysql 관계"
Cohesion: 0.09
Nodes (21): MYSQL_DATABASE, MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD, MYSQL_USER, TZ, interval, retries, start_period (+13 more)

### Community 34 - "BOM 관리 구현 계획 관계"
Cohesion: 0.09
Nodes (13): 작업지시 폼 상태 보존 오류 기록, BOM 관리 구현 및 검증, 원자재 실제 소비 구현 및 검증, 원자재 LOT 등록 및 검증, BOM 관리 구현 계획, Global Constraints, Review Focus, Tasks (+5 more)

### Community 35 - "4. 테이블별 컬럼 명세 관계"
Cohesion: 0.10
Nodes (21): 4.10 work_order, 4.11 finished_goods_lot, 4.12 production_result, 4.13 production_result_correction, 4.14 raw_material_lot, 4.15 material_consumption, 4.16 material_return, 4.17 quality_inspection (+13 more)

### Community 44 - "작업지시 상세·상태 전이 화면 관계"
Cohesion: 0.16
Nodes (18): BOM 버전 상세 화면, BOM 등록·수정 화면, BOM 버전 목록 화면, 원자재 소비 상세 화면, 원자재 소비 등록 화면, 원자재 소비 이력 화면, 원자재 LOT 상세 화면, 원자재 LOT 등록 화면 (+10 more)

### Community 48 - "ProductionResultService"
Cohesion: 0.09
Nodes (5): FinishedGoodsLotController, ProductionResultNotFoundException, CorrectionDetails, ProductionResultService, ProductionResultControllerTests

### Community 53 - "Mini MES Codex Working 관계"
Cohesion: 0.15
Nodes (12): 10. Database Rules, 11. Testing and Verification, 12. AI Development Record, 13. Response Rules, 14. Priority, 1. Project Goal, 3. Source of Truth, 6. Scope Control (+4 more)

### Community 57 - "11. 업무 의사결정 현황 관계"
Cohesion: 0.18
Nodes (11): 11. 업무 의사결정 현황, BD-01. 작업지시 완료 시 지시수량 100% 생산 여부, BD-02. 실제 소비량과 BOM 예상 소요량의 검증 기준, BD-03. 생산실적 정정 및 취소 정책, BD-04. 원자재 투입 정정과 재고 반환 정책, BD-05. 완료 작업지시의 사후 정정, BD-06. 부분 합격 LOT의 품질 상태 및 가용수량, BD-07. 동일 요청 중복 처리 식별 기준 (+3 more)

### Community 58 - "Mini MES 공통 화면 관계"
Cohesion: 0.20
Nodes (9): Mini MES 공통 화면 및 품목 관리 구현 계획, 개발자 확인 명령 및 화면, 개발자 후속 검증, 검토 초점, 구현 순서, 변경 파일 목록, 전역 제약, 정적 검토 기록 (+1 more)

### Community 60 - "application-dev.yml 관계"
Cohesion: 0.20
Nodes (9): com.taehyung.mes, root, logging, level, server, address, spring, thymeleaf (+1 more)

### Community 61 - "Mini MES MySQL 물리 관계"
Cohesion: 0.22
Nodes (8): 11.1 권장 접근, 11.2 LOT 역추적 경로, 11. 주요 조회 구조 및 집계 중복 방지, 12. 대표 시나리오 정적 검토, 1. 설계 개요, 3. 테이블 목록, 6. 인덱스 설계, Mini MES MySQL 물리 DB 설계

### Community 62 - "원자재 투입 및 실제 관계"
Cohesion: 0.22
Nodes (8): 승인된 요청 계약, 업무 검증·트랜잭션·잠금, 원자재 투입 및 실제 소비 확정 구현·검증 안내, 작성한 테스트와 실행 여부, 정합성과 다음 단계, 조회와 표시, 파일, 화면과 URL

### Community 63 - "Routing 구현 및 검증 관계"
Cohesion: 0.22
Nodes (5): 공정 관리 구현 및 검증, Routing 구현 및 검증, 미사용 Routing 개정 직접 편집 결정 필요, 공정 관리 구현 계획, Routing 구현 계획

### Community 64 - "생산계획 구현 및 검증 관계"
Cohesion: 0.22
Nodes (8): Business Decision Required, 개발자 직접 검증, 다음 작업지시 개발, 생산계획 구현 및 검증 안내, 실제 DB와의 정합성, 작성된 테스트와 한계, 추가·수정 파일, 화면과 구조

### Community 65 - "Routing 구현 및 검증 관계"
Cohesion: 0.22
Nodes (8): Business Decision Required — 저장된 미사용 개정의 직접 편집, Routing 구현 및 검증 안내, 개발자 직접 검증, 구현 화면과 구조, 다음 작업지시 구현, 작성한 테스트와 검증 한계, 정책과 트랜잭션, 추가·수정 파일

### Community 66 - "작업지시 구현 및 검증 관계"
Cohesion: 0.22
Nodes (8): 개발자 직접 확인, 남은 정책과 후속 구현, 신규 등록과 최초 사용, 작성된 테스트와 수행 여부, 작업지시 구현 및 검증 안내, 잠금과 상태, 추가·수정 파일, 화면과 URL

### Community 68 - "Parent Lock and Conditional 관계"
Cohesion: 0.29
Nodes (5): Service Business Rules and Transaction Boundary, Material Consumption Contract, Work Order State Transition, Consumption to Return Relationship, UTC and READ COMMITTED Configuration

### Community 69 - "AI Failure - MySQL 관계"
Cohesion: 0.29
Nodes (6): AI Failure - MySQL CHECK 예외 검증 타입, AI Suggestion, Fix, Problem, Risk, Verification

### Community 70 - "AI Failure - 작업지시 관계"
Cohesion: 0.29
Nodes (6): AI Failure - 작업지시 폼의 선택값과 버튼 상태 보존, AI Suggestion, Fix, Problem, Risk, Verification

### Community 71 - "BOM 관리 구현 및 관계"
Cohesion: 0.29
Nodes (6): BOM 관리 구현 및 직접 검증 안내, 개발자가 직접 확인하는 방법, 검증 기록과 한계, 구현 범위, 남은 업무 결정, 추가·수정 파일

### Community 72 - "공정 관리 구현 및 관계"
Cohesion: 0.29
Nodes (6): 개발자가 직접 검증할 순서, 공정 관리 구현 및 검증 안내, 등록·수정 및 이력 정책, 추가·수정 파일, 테스트와 실행 여부, 화면과 구조

### Community 73 - "원자재 LOT 등록·조회 구현 관계"
Cohesion: 0.29
Nodes (6): 다음 단계, 승인 정책과 DB 정합성, 원자재 LOT 등록·조회 구현 및 검증 안내, 작성된 테스트와 실행 여부, 파일, 화면과 URL

### Community 75 - "2. Technology Constraints 관계"
Cohesion: 0.33
Nodes (6): 2. Technology Constraints, Backend, Database, Frontend, Infra, Test

### Community 76 - "4. MES Business Rules 관계"
Cohesion: 0.33
Nodes (6): 4. MES Business Rules, General, Historical Data, LOT, Production Quantity, Work Order

### Community 77 - "생산계획 구현 및 검증 관계"
Cohesion: 0.33
Nodes (3): MySQL CHECK 예외 검증 타입 오류 기록, 생산계획 구현 및 검증, 생산계획 구현 계획

### Community 78 - "10. 동시성 및 중복 관계"
Cohesion: 0.33
Nodes (6): 10.1.1 8차 원자재 소비 승인 계약 및 구현, 10.1 요청 식별 계약 추천, 10.2 결과 연결·원자성, 10.3 검사 요청의 확장과 보장 수준, 10.4 반환·정정 요청의 남은 재시도 문제, 10. 동시성 및 중복 요청 처리

### Community 79 - "BR-06. 품목 기준정보 및 관계"
Cohesion: 0.40
Nodes (5): BR-06-01. 코드와 입력값, BR-06-02. 변경 및 이력 보존, BR-06-03. 작성자 및 저장 정합성, BR-06. 품목 기준정보 및 개발용 작성자, 정상·오류 시나리오

### Community 80 - "BR-11. 작업지시 등록·조회·시작·시작 전 관계"
Cohesion: 0.40
Nodes (5): BR-11-01. 기준 확정·배분·최초 사용, BR-11-02. 상태와 완료 검증 준비, BR-11. 작업지시 등록·조회·시작·시작 전 취소, WO-BD-01. 현재 미사용 공정, WO-BD-02. 신규 지시수량

### Community 82 - "5. Architecture Rules 관계"
Cohesion: 0.50
Nodes (4): 5. Architecture Rules, Controller, Mapper, Service

### Community 83 - "8. Code Modification Workflow 관계"
Cohesion: 0.50
Nodes (4): 8. Code Modification Workflow, 수정 전, 수정 중, 수정 후

### Community 85 - "BR-07. BOM 관리 관계"
Cohesion: 0.50
Nodes (4): BR-07-01. 등록과 수량, BR-07-02. 수정 및 이력, BR-07-03. 저장 및 동시 요청, BR-07. BOM 관리

### Community 86 - "14. 미확정 사항과 구현 관계"
Cohesion: 0.50
Nodes (4): 14.1 남은 업무 결정, 14.2 기술 설계·검증 필요, 14.3 논리 ERD·업무 문서와의 정합성 보고, 14. 미확정 사항과 구현 전 결정 항목

### Community 87 - "2. 사용 DB 및 관계"
Cohesion: 0.50
Nodes (4): 2.1 DB·명명·시간, 2.2 표 읽는 방법과 공통 물리 컬럼, 2.3 정확한 수량 타입과 정책 경계, 2. 사용 DB 및 전제조건

### Community 88 - "5. PK/FK/UNIQUE/CHECK와 보장 범위 관계"
Cohesion: 0.50
Nodes (4): 5.1 주요 제약, 5.2 작업지시 행내 상태 제약 추천, 5.3 유일성 범위가 미확정인 업무 코드와 LOT 번호, 5. PK/FK/UNIQUE/CHECK와 보장 범위

### Community 89 - "7. 상태·코드·버전 및 이력 관계"
Cohesion: 0.50
Nodes (4): 7.1 상태와 품질 판정, 7.2 기준정보 버전 보존, 7.3 생산실적 이력, 7. 상태·코드·버전 및 이력 보존

### Community 90 - "9. 업무별 트랜잭션 관계"
Cohesion: 0.50
Nodes (4): 9.1 공통 전제와 잠금 순서, 9.2 업무별 명세, 9.3 경합과 격리수준, 9. 업무별 트랜잭션

### Community 92 - "6. 생산실적 및 완제품 관계"
Cohesion: 0.67
Nodes (3): 6. 생산실적 및 완제품 LOT, BR-01. 작업지시와 완제품 LOT, 생산실적 수량 정합성

### Community 93 - "7. 품질검사 및 불량 관계"
Cohesion: 0.67
Nodes (3): 7. 품질검사 및 불량 관리, BR-05. 생산실적과 품질검사, 품질 수량 예시

### Community 95 - "13. 설계 선택 근거 관계"
Cohesion: 0.67
Nodes (3): 13.1 실제 반환과 입력 오류는 다른 사건, 13.2 주요 기술 선택, 13. 설계 선택 근거 및 소비 입력 오류 대안

### Community 96 - "8. 수량 저장·집계 전략 관계"
Cohesion: 0.67
Nodes (3): 8.1 원천과 파생값, 8.2 집계 저장의 선택, 8. 수량 저장·집계 전략

### Community 119 - ".setUp"
Cohesion: 0.12
Nodes (3): MaterialReturnMapper, MaterialReturnOutcomeCodec, MaterialReturnTransactionService

### Community 121 - "ProductionResultForm"
Cohesion: 0.11
Nodes (4): ProductionResultCommand, ProductionResultForm, ProductionResultRuleException, ProductionResultContractTests

### Community 124 - "LotTraceDetails"
Cohesion: 0.15
Nodes (4): LotTraceDetails, LotTraceService, MaterialKey, MaterialLotKey

### Community 126 - "QualityInspectionMapper"
Cohesion: 0.15
Nodes (6): QualityInspectionMapper, QualityInspectionNotFoundException, QualityInspectionOutcomeCodec, QualityInspectionHistory, QualityInspectionService, QualityInspectionTransactionService

### Community 127 - ".create"
Cohesion: 0.20
Nodes (3): ItemForm, ItemService, ItemControllerTests

### Community 128 - "ProductionResultMapper"
Cohesion: 0.19
Nodes (3): ProductionResultMapper, ProductionResultOutcomeCodec, ProductionResultTransactionService

### Community 131 - "MaterialReturnForm"
Cohesion: 0.15
Nodes (3): MaterialReturnCommand, MaterialReturnForm, MaterialReturnRuleException

### Community 138 - "ProductionPlanService"
Cohesion: 0.19
Nodes (3): ProductionPlanMapper, ProductionPlanRuleException, ProductionPlanService

### Community 142 - "ItemKind"
Cohesion: 0.15
Nodes (4): ItemKind, FINISHED, RAW, SEMI

### Community 150 - "DashboardMapper"
Cohesion: 0.27
Nodes (3): DashboardMapper, DashboardDetails, DashboardService

### Community 152 - "테스트 구분"
Cohesion: 0.18
Nodes (6): 생산실적·완제품 LOT 구현 및 검증 안내, 실행 여부, 저장·동시성 규약, 테스트 구분, 화면과 URL, ProductionResultMapperTests

### Community 154 - "9차 원자재 반환 구현·검증 기록"
Cohesion: 0.20
Nodes (9): 9차 원자재 반환 구현·검증 기록, MySQL 통합 테스트 실행 조건, 변경 파일, 실행하지 않은 검증, 실행한 검증, 정합성 및 잠금 검토, 중복 요청 계약, 추가 결정 (+1 more)

### Community 155 - "ItemUnit"
Cohesion: 0.20
Nodes (4): ItemUnit, EA, G, M

### Community 156 - "Material Return Implementation Plan"
Cohesion: 0.25
Nodes (7): Global Constraints, Material Return Implementation Plan, Review Focus, Task 1: 입력·저장 계약, Task 2: 화면, Task 3: MySQL 증명·문서·최종 검토, 실행 결과

### Community 158 - "원자재 반환 설계"
Cohesion: 0.29
Nodes (6): 대안과 선택, 목적과 범위, 승인 계약, 원자재 반환 설계, 트랜잭션과 동시성, 화면과 검증

## Knowledge Gaps
- **37 isolated node(s):** `RAW`, `SEMI`, `FINISHED`, `EA`, `M` (+32 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1024 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **98 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MaterialConsumption` connect `원자재 소비` to `애플리케이션 테스트`, `MaterialReturnService`, `org.junit.jupiter.api.Test`, `MaterialConsumptionOutcome 관계`, `MaterialConsumptionCommand 관계`, `.setUp`, `MaterialConsumptionMapper`, `.get`, `LotTraceDetails`, `BomComponent`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `Item` (e.g. with `.missingVersionsAndInactiveProductOrProcessAreRejected()` and `.optionsShowInactiveProcessesAsUnselectableAndCompletionGuardRequiresExactQuantity()`) actually correct?**
  _`Item` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `RAW`, `SEMI`, `FINISHED` to the rest of the system?**
  _37 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `RawMaterialLot` be split into smaller, more focused modules?**
  _Cohesion score 0.10256410256410256 - nodes in this community are weakly interconnected._
- **Why does `WorkOrder` connect `작업지시 도메인` to `loggerfactory 관계`, `WorkOrderStatus`, `작업지시 Mapper 연계`, `BomComponent`, `org.junit.jupiter.api.Test`, `ProductionResultService`, `.create`, `.get`, `list`, `WorkOrderTransitionForm 관계`, `DashboardMapper`, `WorkOrderTotals`, `MaterialConsumptionMapper`, `.setUp`, `LotTraceDetails`, `ProductionResultMysqlIntegrationTests`, `WorkOrderMysqlIntegrationTests 관계`, `MaterialConsumptionOptions`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **Should `공정 관리` be split into smaller, more focused modules?**
  _Cohesion score 0.05554311310190369 - nodes in this community are weakly interconnected._
- **Why does `FinishedGoodsLot` connect `FinishedGoodsLot` to `ProductionResultMapper`, `ProductionResultService`, `.get`, `list`, `WorkOrderTransitionForm 관계`, `WorkOrderTotals`, `LotTraceDetails`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._