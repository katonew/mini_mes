# 생산계획 구현 및 검증 안내

## 화면과 구조

| 화면 | GET | POST |
|---|---|---|
| 계획 ID·제품 코드/명칭 검색 및 목록 | /production-plans?planId=&code=&name= | 없음 |
| 신규 등록 | /production-plans/new | /production-plans |
| 기본정보·기존 작업지시 참조 건수 | /production-plans/{planId} | 없음 |

- ProductionPlanController → ProductionPlanService → ProductionPlanMapper/ProductionPlanMapper.xml → MySQL.
- 조회/저장 모델은 ProductionPlan, 등록 입력 DTO는 ProductionPlanForm이다. 수정 정책이 미확정이므로 수정 DTO·API·화면을 임의로 만들지 않는다.
- 기존 공통 레이아웃·CSS·mes.js 폼 제출/중복 클릭 억제/성공 리다이렉트를 재사용한다. 신규 프레임워크·의존성은 없다.

## 추가·수정 파일

- 신규 Java: ProductionPlan.java, ProductionPlanForm.java, ProductionPlanRuleException.java, ProductionPlanNotFoundException.java, ProductionPlanMapper.java, ProductionPlanService.java, ProductionPlanController.java.
- 신규 SQL/UI: ProductionPlanMapper.xml, 생산계획용 list.html/form.html/detail.html.
- 신규 테스트: ProductionPlanServiceTests.java, ProductionPlanControllerTests.java, ProductionPlanMapperTests.java, ProductionPlanMysqlIntegrationTests.java.
- 공통 수정: layout.html, error.html, PageExceptionHandler.java, messages.properties. 품목·BOM·공정·Routing 업무 코드와 CSS/JS·schema.sql·의존성은 변경하지 않는다.
- 문서: business-rules.md(BR-10), mini_mes_prd.md(FR-05), database-design.md, erd-design.md, 2026-10-26-production-plan.md, production-plan-verification.md.
- 실제 AI 오류 기록: mysql-check-exception-assertion.md. MySQL CHECK 예외 하위 타입을 고정했던 테스트 가정을 정적 리뷰로 수정했다.
- 기존 공정·Routing 미커밋 변경은 보존했으며 이번 작업에서 커밋·push하지 않았다.

## 실제 DB와의 정합성

production_plan은 PK, 제품 FK, planned_qty, planned_date, 생성 시각/작성자, 수정 시각만 저장한다. **계획코드·상태·BOM/Routing FK 및 업무 UNIQUE가 없다.** 계획 ID는 코드와 다른 DB 자동 생성 식별자다. 상태 필터·코드 입력/중복 테스트를 만들기 위해 컬럼을 추가하지 않았다.

- FINISHED 활성 품목을 품목 행 잠금 후 검증하고 계획 한 행을 Service 트랜잭션으로 저장한다. 검증 실패·영향 행수 불일치·생성 ID 누락은 성공 처리하지 않는다.
- 요청의 양수 규칙과 DB의 정수 CHECK에 따라 신규 수량은 1~999999999999다. DECIMAL(18,6)의 소수 저장 용량이 있더라도 현재 CHECK가 소수를 금지한다. EA만 새로 제한하지 않으며 다른 단위도 현행 스키마상 정수만 가능하다. 반올림·절삭·환산은 없다.
- 날짜는 필수 ISO LocalDate, 유효한 달력 날짜와 MySQL DATE 범위를 검증한다. 과거 날짜 차단은 미정 정책이라 추가하지 않는다.
- 작성자는 ActorProvider/MES_ACTOR_ID 재사용. 입력 폼은 제품·수량·계획일만 바인딩한다.
- work_order.production_plan_id → production_plan.production_plan_id이며 1 : 0..N이다. 기존 작업지시 참조 건수만 조회하고 연결·수량·상태를 변경하지 않는다.
- BOM 버전과 Routing 개정의 실제 참조는 work_order에 있다. 계획에서 버전을 자동 선택하거나 스냅샷을 생성하지 않는다. 작업지시·LOT 생성·소비/재고 변경·실적 등록도 하지 않는다.
- 현재 수정·삭제 API/SQL이 없어 작업지시 참조 계획을 직접 변경할 수 없다. 상세의 건수를 계획 상태/배분량/달성률로 표시하지 않는다.

## Business Decision Required

| 항목 | 확인된 근거 | 현재 적용 및 추천 | 후속 대안과 영향 |
|---|---|---|---|
| 계획코드·상태 | 실제 컬럼 없음 | 기존 PK로 조회하고 상태를 만들지 않는다 | 업무코드·채번·유일성, 상태 전이를 정한 후 별도 스키마 변경 작업 |
| 수정·취소·삭제 | FR-05 및 DB 설계 미확정 | 등록·조회만 제공하고 기존 참조 보존 | 변경 허용 필드, 배분된 수량과의 관계, 참조 후 제품 변경 금지, 동시성 정책 결정 필요 |
| 중복 요청 | 업무 UNIQUE/계획 요청 키 없음 | 동일 제품/수량/날짜를 임의로 중복으로 선언하지 않는다 | 코드 또는 요청 식별 계약 및 DB 보호 구조 필요. 화면 버튼 억제는 재전송 멱등성이 아님 |
| 계획 배분·달성 | 초과 배분·집계 기준 미확정 | 작업지시 참조 건수만 조회 | 작업지시 설계에서 생산 달성과 배분 기준을 구분하여 결정 |

이번 등록·조회 구현에 추가 결정은 필요하지 않다. 위 항목을 구현할 때 별도 확정해야 한다.

## 작성된 테스트와 한계

- Service: 정상 등록, 제품 미존재/RAW/미사용, 0·음수·소수·범위 초과, 필수값, 날짜 누락/범위, 작성자, 목록/검색/미존재, 저장 실패·생성키 누락을 검증하도록 작성했다.
- Mock Spring 트랜잭션 프록시는 rollback 호출을 확인한다. 실제 MySQL 롤백·잠금 검증은 아니다.
- Controller: 엄격한 잘못된 날짜·숫자 바인딩, 필수 날짜 오류 모델, 목록/등록/상세, 404/503, 참조 계획의 POST/DELETE 405를 검증하도록 작성했다. standalone MockMvc이므로 HTML 렌더링을 검증하지 않는다.
- Mapper: 계획 INSERT 컬럼, UPDATE/DELETE 부재, 작업지시 참조 조회 전용 SQL 계약을 검사하도록 작성했다. 실제 FK 데이터 보존 검증은 아니다.
- MySQL 통합: 실제 등록/검색/작업지시 미생성, INSERT 후 실제 DB CHECK 오류 발생 시 앞선 INSERT 롤백, 품목 비활성화와 잠금 경합을 검증하도록 작성했다. 오류 주입에 spy를 사용하지만 INSERT·오류 SQL·트랜잭션·결과 조회는 실제 DB를 사용한다.
- 계획코드 중복 테스트는 컬럼과 유일성 규칙이 없어 작성하지 않았다. DB PK 유일성만으로 업무 중복 방지를 보장한다고 주장하지 않는다.
- **모든 테스트 미실행.** 빌드·린트·브라우저·렌더링·개발서버·DB도 실행하지 않았다. 운영 OCI 접속/데이터 변경 없음. 소스 정적 검토와 읽기 전용 리뷰만 수행했다.

## 개발자 직접 검증

운영 OCI가 아닌 격리된 환경에서 수행한다.

1. /production-plans에서 빈 목록·계획 ID·완제품 코드/명칭 검색과 메뉴 이동을 확인한다. 없는 상태 필터·계획코드는 표시되지 않아야 한다.
2. 활성 FINISHED 제품·계획수량 100·유효 계획일로 등록하여 생성 ID, 상세 수량/날짜/등록 시각을 확인한다.
3. 0·음수·소수·12자리 초과, 날짜 누락/2026-02-30, RAW·없는·미사용 제품 변조 요청을 서버에서도 거절하는지 확인한다.
4. MES_ACTOR_ID 누락·공백·64자 초과, DB 오류에서 성공 안내 없이 폼 입력과 오류가 유지되는지 확인한다.
5. 계획 등록 전후 작업지시/LOT/재고 데이터가 자동 변경되지 않는지 확인한다. 기존 유효 작업지시 fixture가 있으면 해당 계획 참조 건수와 조회만 가능한지를 확인한다. 이번 기능으로 작업지시 fixture를 생성할 수는 없다.
6. 생산계획 상세 POST/DELETE는 405이며 BOM·Routing 확정 입력은 없어야 한다. 현재 품목이 미사용으로 바뀌어도 기존 계획의 조회·참조는 보존되어야 한다.

Mock/SQL 계약 테스트 명령(이번에 실행하지 않음):

```powershell
.\gradlew.bat test --tests "com.taehyung.mes.production.ProductionPlanServiceTests" --tests "com.taehyung.mes.production.ProductionPlanControllerTests" --tests "com.taehyung.mes.production.ProductionPlanMapperTests"
```

실제 MySQL 테스트는 기본 비활성이다. 기존 schema.sql이 적용된 별도 로컬 전용 스키마·전용 계정을 개발자가 먼저 준비해야 하며 자동 DDL은 없다.

- MES_PLAN_MYSQL_TEST=LOCAL_TEST_ONLY 명시 필수.
- PLAN_TEST_DB_NAME은 소문자·숫자·밑줄이며 `_plan_test`로 끝나는 전용 스키마여야 한다.
- PLAN_TEST_DB_USER/PLAN_TEST_DB_PASSWORD 필수. PLAN_TEST_DB_PORT 선택(기본 3306).
- 호스트는 127.0.0.1 고정. 운영으로 향하는 터널·프록시를 사용하지 않는다.
- UUID 작성자로 만든 테스트 계획/품목을 기록하고 자기 fixture만 정리한다. 전용 테스트 DB에 데이터 쓰기·삭제가 발생한다.
- 일반 DB_* 또는 application.yml로 연결하지 않는다. 공통 Gradle test 준비 단계의 DB 환경값 요구와는 구분된다.

```powershell
.\gradlew.bat test --tests "com.taehyung.mes.production.ProductionPlanMysqlIntegrationTests"
```

## 다음 작업지시 개발

계획과 지시의 제품 일치를 검증하고 정확한 BOMVersion/RoutingRevision PK를 저장해야 한다. 계획 행·기준정보 헤더·BOMVersion·RoutingRevision·품목/공정 잠금 순서를 기존 규약과 함께 확정한다. 초과 배분 정책이 미정인 상태에서 임의의 100% 배분 제한이나 자동 계획 축소를 추가하지 않는다. first_used_at 설정과 작업지시 저장은 같은 트랜잭션에서 수행한다.
