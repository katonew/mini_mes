# 작업지시 구현 및 검증 안내

> 이 문서는 6차 작업지시 구현 당시 기록이다. 10차 생산실적·완제품 LOT·완료 동작은 BR-15와 production-result-verification.md의 후속 기록을 따른다.

## 화면과 URL

| 기능 | GET | POST |
|---|---|---|
| ID·계획 ID·완제품 코드/명칭·상태 검색 | /work-orders | 없음 |
| 생산계획 선택 및 기준 조회 | /work-orders/new?planId= | 없음 |
| 신규 등록 | /work-orders/new?planId= | /work-orders |
| 기본정보·확정 기준·스냅샷·기존 누적 실적 | /work-orders/{id} | 없음 |
| 시작·시작 전 취소 | 상세의 READY 상태 버튼 | /work-orders/{id}/transitions |

WorkOrderController → WorkOrderService → WorkOrderMapper/XML 구조다. BomMapper·RoutingMapper·ItemMapper·ProcessMapper의 기존 조회·잠금 기능과 ActorProvider를 재사용한다. 생산계획을 GET으로 조회하면 해당 제품의 후보만 표시하며 최신 버전을 자동 선택하지 않는다. 공통 레이아웃·CSS·mes.js를 재사용하고 workorder.js는 배분 정보 계산과 작업지시 저장 버튼 상태 보존만 담당한다.

## 추가·수정 파일

- 신규 Java: WorkOrder.java, WorkOrderForm.java, WorkOrderTransitionForm.java, WorkOrderStatus.java, WorkOrderTotals.java, WorkOrderDetails.java, WorkOrderAllocation.java, WorkOrderBomOption.java, WorkOrderRoutingOption.java, WorkOrderOptions.java, WorkOrderRuleException.java, WorkOrderNotFoundException.java, WorkOrderMapper.java, WorkOrderService.java, WorkOrderController.java.
- 신규 SQL/UI: WorkOrderMapper.xml, 작업지시용 list.html/form.html/detail.html, workorder.js.
- 신규 테스트: WorkOrderServiceTests.java, WorkOrderControllerTests.java, WorkOrderMapperTests.java, WorkOrderMysqlIntegrationTests.java.
- 공통 수정: layout.html, error.html, PageExceptionHandler.java, messages.properties.
- 문서: business-rules.md(BR-11/WO-BD-01/02), mini_mes_prd.md(FR-06), database-design.md, erd-design.md, routing-verification.md, production-plan-verification.md, 2026-10-26-work-order.md, work-order-verification.md.
- AI 실패 기록: work-order-form-state-preservation.md.
- 기존 schema.sql·의존성·기준정보 업무 Service/Mapper·공통 CSS/mes.js는 변경하지 않았다. 앞선 단계 미커밋 변경을 보존하며 커밋·push하지 않았다.

## 신규 등록과 최초 사용

- 승인된 WO-BD-01: 선택 Routing 개정의 모든 현재 공정을 PK 순서로 잠그고 활성 확인한다. 미사용이 하나라도 있으면 신규 등록을 거부하고 후보에도 사유를 표시한다. 재활성화 후 다시 검증한다. 기존 지시는 현재 공정 활성값 때문에 자동 중단·취소하지 않는다.
- 승인된 WO-BD-02: 지시수량은 1~999999999999 정수다. 0·음수·소수·범위 초과는 거절하고 절삭/반올림하지 않는다. DB의 ordered_qty >=0보다 엄격한 업무 규칙이며 DDL은 그대로다.
- 생산계획·요청 제품·BOM 헤더 제품·Routing 헤더 제품 일치를 검증하고 신규 대상 FINISHED 활성 상태를 확인한다. BOM의 제품 단위와 기준 생산수량·기존 구성 자재의 단위/수량 정합성 및 공정 구성 존재도 검증한다.
- 계획 1:N 지시 관계를 유지한다. 선택한 정확한 bom_version_id와 routing_revision_id를 저장하며 기존 지시를 최신 기준으로 재연결하지 않는다.
- 모든 검증 후 first_used_at이 NULL인 버전/개정만 DB UTC 시각으로 채우고 작업지시 INSERT를 같은 트랜잭션에서 수행한다. 기존 값은 유지하며 이후 취소하더라도 해제하지 않는다. 저장 실패 시 사용 표시와 지시가 함께 롤백된다.
- 배분 정보는 등록 지시수량 합계(취소 포함), 취소분, 등록 후 예상 합계, 계획 대비 차이를 구분한다. 초과는 정보성 안내이며 차단하지 않는다. 취소분 재배분 상한·계획 변경/취소 정책을 정하지 않으며 합계를 완료 수량/달성률로 부르지 않는다.
- 신규 등록 요청 키/멱등성은 미확정이므로 동일 내용의 요청이 별도 지시로 저장될 수 있다. PK·화면 버튼만으로 영속적인 중복 요청 방지를 주장하지 않는다. 네트워크 오류 후에는 목록에서 등록 결과를 먼저 확인한다.

## 잠금과 상태

등록은 READ_COMMITTED에서 **Plan → BOM 헤더 → Routing 헤더 → BOMVersion → RoutingRevision → Item(PK순) → Process(PK순)**이다. 초기 부모 식별용 일반 조회 이후 실제 검증은 잠긴 최신 행으로 수행한다. 기존 BOM 편집과 Routing 개정 추가의 상대 순서를 유지한다. 명시적 격리수준은 초기 위치 조회가 만든 오래된 REPEATABLE_READ 스냅샷으로 이후 구성 목록을 읽는 상황을 방지한다.

시작/취소는 WorkOrder를 먼저 잠그고 현재 상태·투입/LOT 이력을 재확인한다. Plan/기준정보 잠금을 역순 요청하지 않는다. 미래 실적/소비 Service도 같은 WorkOrder 잠금 규약을 지켜야 한다. SQL 오류·교착·잠금 대기는 전체 실패로 처리하며 부분 재시도나 수량 보정을 하지 않는다.

- READY→IN_PROGRESS: started_at/started_by 기록.
- READY→CANCELLED: cancelled_at/cancelled_by 기록. 투입/LOT 이력이 있으면 거부한다.
- 두 전이 모두 기존 실행 이력이 있는 비정상 READY를 일반적인 시작 전 상태로 처리하지 않는다.
- 재시작·진행 중 취소·완료/취소 지시 재변경·임의 COMPLETED 입력을 거절한다. 같은 상태 요청은 다시 시각을 덮어쓰지 않고 오류로 응답한다.
- 작성자·시작자·취소자는 기존 MES_ACTOR_ID이며 화면에서 입력받지 않는다. 상태·실행 시각·first_used_at도 요청으로 바인딩하지 않는다.
- 완료 조건은 IN_PROGRESS, 실제 실적 건수>0, 누적 생산수량>0 및 지시수량 일치다. validateCompletion은 검증만 수행하며 완료 UPDATE/버튼은 없다. 실제 실적 구현 시 상태 변경·검사대상 확정과 원자 처리해야 한다.
- 실적은 finished_goods_lot→production_result의 현재 저장값만 집계하며 정정 이력을 중복 합산하지 않는다. 이번 기능은 실적·LOT·소비·품질 데이터를 만들지 않는다.

## 작성된 테스트와 수행 여부

- Service/Mock: 정상 등록·계획의 복수 지시·초과 배분 비차단, 계획/버전 제품 불일치·미존재·활성, 수량 경계, first_used 기존 값 유지/최초 기록 호출, 잠금 호출 순서, 시작/취소·금지 전이·실적 없는 완료·정확한 완료 수량 조건, 검색·상세·404를 검증하도록 작성했다.
- Mock Spring 프록시 테스트는 오류 시 TransactionManager.rollback 호출만 확인한다. 실제 DB rollback 검증이 아니다.
- Controller/standalone MockMvc: 목록·등록·전이·오류 모델·404/503·조회 장애 시 제출 ID 보존을 검증하도록 작성했다. HTML 렌더링·브라우저 이벤트 검증은 아니다.
- Mapper XML 계약: first_used NULL 조건, 완료/삭제/실적 생성 SQL 부재, 참조 미변경 상태 UPDATE, 현재 실적 집계 경로를 검사하도록 작성했다.
- 실제 MySQL 테스트: FK를 갖춘 독립 fixture로 등록/정확한 참조/첫 사용 보존, CHECK 실패 후 지시+양쪽 사용 표시 전체 롤백, 사용 이후 BOM 편집 거부, 취소 후 사용 표시 유지, 한 계획의 동시 복수 등록, 동시 시작/취소 한 건 반영, 공정 비활성화 잠금 경합·재활성화·기존 지시 진행 보존을 검증하도록 작성했다.
- 실제 CHECK 오류 주입에는 Mapper spy를 쓰지만 선행 INSERT/표시 UPDATE·실패 SQL·트랜잭션·결과 조회는 실제 DB를 사용한다. MySQL 3819/HY000은 DataAccessException과 원인 SQLException 코드로 검증한다.
- **모든 테스트 미실행.** 빌드·린트·브라우저·렌더링·개발서버·DB 실행과 운영 OCI 접근은 하지 않았다. 소스 정적 대조와 독립 읽기 전용 리뷰만 수행했다.
- 리뷰에서 제출 선택값 소실·pageshow의 버튼 재활성화 문제를 보완하고, 통합 테스트 연결의 READ_COMMITTED도 명시했다. 수정 후 실행 검증은 하지 않았다.

## 개발자 직접 확인

격리된 로컬 테스트 환경에서 확인한다.

1. 계획·BOM·Routing이 같은 제품을 참조하는 fixture를 준비하고 /work-orders/new에서 계획을 조회한다. 다른 제품 버전은 나타나지 않아야 하며 최신이 자동 선택되지 않아야 한다.
2. 미사용 공정이 포함된 개정은 사유와 함께 비활성화되고 변조 POST도 서버가 거절해야 한다. 공정을 재활성화하면 새 조회/등록에서 다시 판단한다.
3. 양의 정수 지시를 등록하고 PK 참조 및 두 최초 사용 시각을 확인한다. 같은 버전으로 복수 등록해 시각이 유지되고 계획수량 초과는 안내만 하는지 확인한다.
4. 제품 불일치·없는 버전·0/음수/소수/범위 초과를 서버에서도 거절하는지 확인한다.
5. READY 시작/취소와 동시 요청에서 한 전이만 반영되는지, 취소해도 first_used_at이 유지되는지 확인한다. 현재 공정이 비활성화되어도 기존 READY 지시 시작을 자동 차단하지 않아야 한다.
6. 상세의 저장 공정명·기존 실적 합계를 확인한다. 실제 실적이 없는 지시를 완료시키는 버튼/쓰기 API는 없어야 한다.
7. 저장/선택 조회 장애에서 선택 ID·수량이 오류 화면에 보존되고, 조회 불가/비활성 제품의 저장 버튼이 pageshow 후에도 활성화되지 않는지 확인한다.

Mock/SQL 테스트 명령(이번 작업에서 실행하지 않음):

```powershell
.\gradlew.bat test --tests "com.taehyung.mes.workorder.WorkOrderServiceTests" --tests "com.taehyung.mes.workorder.WorkOrderControllerTests" --tests "com.taehyung.mes.workorder.WorkOrderMapperTests"
```

실제 MySQL 테스트는 기본 비활성이다. 개발자가 기존 schema.sql을 적용한 전용 로컬 스키마와 계정을 준비해야 한다. 자동 DDL/운영 스키마 연결은 없다.

- MES_WO_MYSQL_TEST=LOCAL_TEST_ONLY 필수.
- WO_TEST_DB_NAME은 소문자/숫자/밑줄이며 `_wo_test`로 끝나는 별도 스키마여야 한다.
- WO_TEST_DB_USER/WO_TEST_DB_PASSWORD 필수, WO_TEST_DB_PORT 선택(기본3306), 호스트127.0.0.1 고정. 운영으로 향하는 터널/프록시 금지.
- 테스트 연결 격리수준은 READ_COMMITTED로 지정하고 메타데이터도 검사하도록 작성했다.
- 고유 UUID 작성자로 만든 자기 fixture만 FK 역순으로 정리한다. 전용 테스트 DB에 실제 쓰기/삭제가 발생하며 운영 OCI에는 실행하지 않는다.
- 기본 DB_* / application.yml로 연결하지 않는다. 공통 Gradle test 준비 단계의 DB 환경값 요구와는 구분된다.

```powershell
.\gradlew.bat test --tests "com.taehyung.mes.workorder.WorkOrderMysqlIntegrationTests"
```

## 남은 정책과 후속 구현

이번 등록·조회·시작·취소를 막는 미확정 정책은 없다. 신규 등록 요청의 영속적 중복 식별 계약, 계획 변경/취소·초과 배분 상한은 미확정으로 유지한다. 생산실적 구현에서는 WorkOrder 잠금 후 실적·LOT 원자 저장, 누적 생산 상한, 완료와 inspection_target_qty 확정의 트랜잭션을 연결해야 한다. 상태 변경 뒤 버전 편집 잠금을 역순 요청하거나 현재 BOM/Routing으로 과거 참조를 재계산하지 않는다.
