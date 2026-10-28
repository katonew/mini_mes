# 원자재 투입 및 실제 소비 확정 구현·검증 안내

## 화면과 URL

| 기능 | URL |
|---|---|
| 작업지시·LOT 번호·원자재 코드/명칭 검색 | GET /material-consumptions |
| 신규 투입/작업지시·확정 BOM 구성 기준 조회 | GET /material-consumptions/new |
| 실제 소비 확정 | POST /material-consumptions |
| 소비 원본·최초 성공 재고 전후값·현재 재고 조회 | GET /material-consumptions/{consumptionId} |

원자재 투입 메뉴를 연결하고 작업지시 상세에 투입 이력과 IN_PROGRESS의 신규 투입 링크를 추가했다. 공통 레이아웃/CSS/mes.js와 기존 GET 기준 조회·POST 오류 재표시 흐름을 사용한다. 별도 프레임워크·라이브러리·JavaScript는 추가하지 않았다.

## 승인된 요청 계약

- BR-13 / database-design.md 10.1.1절: 작성자별 scope는 원본 MES_ACTOR_ID UTF-8 SHA-256의 소문자 hex 64자리, operation은 MATERIAL_CONSUME 서버 상수다. 이는 인증/권한 모델이 아니라 기존 개발용 작성자 식별을 재사용한다. 작성자가 달라지면 같은 UUID도 다른 요청 범위다.
- 신규 GET에서 UUID를 생성한다. GET 기준 조회와 POST 입력/저장 오류는 같은 키를 유지하며 POST 오류 경로에서 새 키를 만들지 않는다. 새로운 소비 작업은 신규 등록 링크에서 시작한다. 키는 표준 UUID 소문자 형식으로 정규화한다.
- MaterialConsumptionCommand.from이 선택 ID·양수/DECIMAL 범위/scale<=6·작성자·UUID를 검증하고 수량을 정규화한다. 이후 command의 동일 값으로 fingerprint와 실제 INSERT를 구성한다. `1`과 `1.000000`은 같고 소수 6자리 초과는 정규화 전에 거부한다.
- fingerprint는 `MATERIAL_CONSUME:v1` 헤더와 LF로 구분한 `workOrderId`, `bomComponentId`, `rawLotId`, `consumedQty`의 고정 순서 UTF-8 SHA-256 32바이트다. 마지막 LF는 없다. 소비/Receipt 자동 PK·서버 생성 시각·현재 재고는 제외한다. 정규화 문자열의 ID/수량에는 구분문자가 들어갈 수 없어 필드 경계가 모호하지 않다.
- 최초 성공 snapshot v1은 소비/지시/구성/LOT ID, 정규화 수량·단위, UTC 소비시각, 당시 가용수량 전후값을 보존한다. 수량은 십진 문자열, 시각은 UTC `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`다. 기존 JSON 컬럼을 사용하며 재고 전후 컬럼을 신설하지 않았다.
- 동일 범위·UUID·payload는 최초 성공 결과로 리다이렉트한다. 현재 지시가 완료/취소되거나 재고가 달라져도 새 업무 검증/차감/소비 INSERT를 하지 않는다. 다른 payload는 키 재사용 오류다. 실패한 시도의 Receipt는 롤백되므로 해당 UUID를 영구 실패 기록으로 점유하지 않는다.
- 성공 결과와 실제 소비 FK/수량/단위/시각을 대조하고, Receipt가 소비 한 건에만 연결되며 반환·실적·검사에 동시에 연결되지 않는지 확인한다. 미완성·불일치 JSON/원장은 성공으로 복구하지 않는다. 완료 Receipt의 삭제·만료·덮어쓰기 기능은 없다.

## 업무 검증·트랜잭션·잠금

일반 MaterialConsumptionService는 검증한 command를 별도 Bean인 MaterialConsumptionTransactionService로 전달한다. create는 REQUIRES_NEW/READ_COMMITTED이며 다음 순서다.

1. 기존 request_receipt UNIQUE로 선점 INSERT한다. 이 시점에 별도 커밋하지 않는다.
2. 기존 WorkOrderMapper.findByIdForUpdate로 작업지시를 잠그고 IN_PROGRESS를 재확인한다.
3. 해당 지시의 bom_version_id와 선택한 bom_component_id 관계·제품/보존 단위를 검증한다. 최신 BOM을 조회하지 않는다.
4. RawMaterialLot 행만 FOR UPDATE로 잠그고 품목/보존 단위를 선택 구성과 대조한다. EA 정수 조건을 검사한다.
5. 가용량을 확인하고 `available_qty>=quantity AND quantity>0` 조건으로 차감한다. 영향행수 1을 요구한다.
6. 같은 정규화 수량으로 material_consumption을 INSERT하고 SQL UTC_TIMESTAMP(6) 소비시각을 읽는다.
7. 최초 성공 JSON과 processed_at을 함께 기록하고 결과 연결이 정확히 한 건인지 검증한 후 커밋한다.

Receipt INSERT의 DuplicateKeyException만 별도 예외로 바꿔 프록시 밖에 전달한다. 프록시가 먼저 롤백을 마치면 일반 Service가 replay를 외부 프록시로 호출한다. replay는 별도 REQUIRES_NEW/READ_COMMITTED 읽기 트랜잭션이다. 선행 경쟁 요청의 커밋 후 UNIQUE 오류가 발생하면 완료된 기존 결과를 새 범위에서 확인한다. 선행 요청이 롤백하면 대기 중 INSERT가 새 요청으로 진행할 수 있다. 미완성 커밋·중복 예외 무시·동일 실패 트랜잭션 내 무조건 재조회에 의존하지 않는다.

전체 실패 시 Receipt·재고·소비·성공 결과를 모두 롤백한다. 교착/잠금 대기를 부분 성공이나 자동 부분 재시도로 처리하지 않는다. 저장 결과 불확실 시 같은 키/입력으로 이력 조회 및 재전송하도록 안내한다.

기존 BOM 편집은 헤더→버전 잠금 후 first_used_at 또는 작업지시 참조가 있으면 거부한다. 이미 확정된 지시의 BOM은 이 규약으로 불변이며 소비에서 WorkOrder 뒤에 BOM/Item/Process 편집 잠금을 역순으로 추가하지 않았다. 새 작업지시 생성의 상위 잠금과 별개로, 기존 지시의 상태 변경/소비는 같은 WorkOrder 행을 공유한다. 다른 지시에서 같은 LOT을 소비할 때도 RawLot 행과 조건부 차감이 재고 경쟁을 보호한다. SQL 직접 기준정보 변경까지 애플리케이션이 보장한다고 주장하지 않는다.

현재 품목의 미사용 전환만으로 기존 지시/LOT를 자동 무효화하는 새 정책은 추가하지 않았다. 기존에 확정한 구성과 보존 단위를 사용한다. 단위 환산·예상량 초과 자동 차단·재고 자동 보정은 없다.

## 조회와 표시

- 기준 조회는 REPEATABLE_READ 읽기 트랜잭션으로 지시·구성·독립 소비/반환 합계·LOT 후보의 한 스냅샷을 읽는다. 저장에서는 최신 잠금 상태로 검증한다.
- 예상량은 지시수량×같은 원자재/보존 단위의 required_qty 합/base_qty다. 실제 소비와 원소비에 연결된 반환은 별도 합계로 계산하며 순소비=소비−반환이다. 예상량/순소비 차이는 정확한 BigDecimal 값 또는 나눗셈 식으로 표시한다. 같은 원자재가 여러 구성행에 있으면 품목 합계가 반복 표시된다는 안내를 둔다.
- 소비 원본은 append-only이며 반환이 있어도 consumed_qty를 덮어쓰지 않는다. 기존 반환 데이터 조회만 하며 반환 INSERT/UPDATE/DELETE는 구현하지 않았다.
- 상세의 당시 재고 전후값은 snapshot, 현재 가용수량은 raw_material_lot이다. 최초 성공 값을 현재 재고로 다시 계산하지 않는다. UTC 조회 모델을 변경하지 않고 공통 KstDateTimeFormatter로 화면만 Asia/Seoul 변환한다.
- 화면 바인딩은 workOrderId·bomComponentId·rawLotId 선택, consumedQty·requestKey만 허용한다. 선택 ID는 서버가 실제 관계를 검증하며 생성 PK·시각·작성자·scope·hash·단위·재고 전후값은 바인딩하지 않는다.

## 파일

- Java: MaterialConsumption.java, MaterialConsumptionForm.java, MaterialConsumptionCommand.java, MaterialConsumptionMaterial.java, MaterialConsumptionOptions.java, MaterialConsumptionReceipt.java, MaterialConsumptionOutcome.java, MaterialConsumptionOutcomeCodec.java, MaterialConsumptionMapper.java, MaterialConsumptionService.java, MaterialConsumptionTransactionService.java, MaterialConsumptionController.java, MaterialConsumptionRuleException.java, MaterialConsumptionNotFoundException.java, MaterialConsumptionRequestExistsException.java.
- SQL/화면: MaterialConsumptionMapper.xml, 소비용 list.html/form.html/detail.html.
- 공통 연결: layout.html, error.html, PageExceptionHandler.java, messages.properties, 작업지시용 detail.html.
- 테스트: MaterialConsumptionContractTests.java, MaterialConsumptionServiceTests.java, MaterialConsumptionControllerTests.java, MaterialConsumptionMapperTests.java, MaterialConsumptionMysqlIntegrationTests.java.
- 문서: mini_mes_prd.md, business-rules.md, database-design.md, erd-design.md, material-consumption-verification.md, 2026-10-27-material-consumption.md.
- 기존 원자재 LOT 구현 변경을 보존했다. schema.sql·의존성·시간 설정·기존 업무 Service/Mapper·공통 CSS/JavaScript는 수정하지 않았다.

## 작성한 테스트와 실행 여부

- 계약: 작성자 scope ASCII/안정성, 정규화 수량과 hash 동일성, 다른 payload, 잘못된 ID/UUID/작성자/수량, JSON v1 UTC 마이크로초 round-trip, 최초 잔고 보존·원장 불일치 거부, KST 표시, 정확한 예상량/차이.
- Service/Mock: 정상 소비와 예상량 초과 허용, 잠금/쓰기 순서, 비진행 상태·미존재 지시, 확정 BOM 외 구성·LOT 품목/단위 불일치, 수량·EA/M/G 정밀도, 재고 부족·조건부 차감 실패, 같은/다른 payload 재전송, 미완료 Receipt 거부, 잠금 오류 및 성공 기록 실패 rollback 호출. Spring 프록시 테스트는 실패 롤백 후 두 번째 REQUIRES_NEW 읽기가 시작되는 순서를 검사한다. Mock은 실제 DB 정합성 검증이 아니다.
- Controller/standalone MockMvc: GET UUID 생성/유지, 입력 오류·저장/잠금 오류의 payload 보존, 서버 필드 바인딩 억제, 같은 결과 상세 리다이렉트, 소비 수정 POST 부재, 목록/상세/404/503/업무 오류. HTML 렌더링 테스트는 아니다.
- Mapper/UI 계약: 기존 컬럼과 서버 operation, 조건부 재고 감소, UTC_TIMESTAMP(6), 미완성 Receipt 완료 조건, 제외 업무 쓰기 부재, 고정 BOM 연결·독립 집계·KST·양쪽 폼의 요청 키.
- MySQL 코드: 정상 EA 소비·UTC시각·Receipt 완료, 1/1.000000 재전송, 예상량 초과/0 잔고, 후속 소비 및 완료/취소 상태 이후 최초 결과 재응답, 동일 요청 동시 전송의 커밋 대기·한 번 차감, 같은/다른 작업지시의 같은 LOT 재고 경쟁, 부족 시 전체 미반영·롤백된 키 재사용, 소비 INSERT 후 실제 CHECK 실패·Receipt 성공 CHECK 실패·완료 기록 후 예외의 전체 rollback, 상태 잠금 경합, 실제 lock wait timeout(1205) 실패 전체 롤백을 검증하도록 작성했다.
- MySQL 상태 변경 fixture는 완료/취소 후 성공 재전송과 잠금 검증을 위한 전용 DB 데이터 설정이다. 기존 업무 API에 진행 중 취소나 완료 처리를 새로 추가한 것이 아니다. 실제 deadlock 강제 재현은 포함하지 않고 해당 오류 전파/rollback은 Mock, 실제 MySQL 잠금 오류는 lock wait timeout 사례로 구분한다.
- **이번 작업에서 Java 컴파일·빌드·테스트·서버·브라우저·DB 연결·DDL 실행은 하지 않았다. 실제 MySQL 동시성·롤백·Thymeleaf 렌더링 성공은 미확인이다.**
- 정적 확인: 외부 DTD 접근 없이 Mapper XML을 파싱했고, Java Mapper의 14개 메서드와 SQL 14개 statement 이름이 일치했다. 기존 DDL의 컬럼/UNIQUE/FK/CHECK, 메뉴/URL/바인딩 연결, 예외 후 프록시 호출 경계를 대조했다. 기존 스키마·빌드/시간 설정·업무 Service/Mapper·공통 CSS/JavaScript에 diff가 없었고 변경된 기존 파일의 git diff --check에서 공백 오류가 없었다.

통합 테스트는 MES_CONSUME_MYSQL_TEST=LOCAL_TEST_ONLY와 CONSUME_TEST_DB_NAME(접미사 *_consume_test), CONSUME_TEST_DB_USER, CONSUME_TEST_DB_PASSWORD를 모두 요구한다. 호스트는 127.0.0.1 고정, 포트는 CONSUME_TEST_DB_PORT(기본 3306)다. 일반 DB_* 및 application.yml을 읽지 않고 기존 schema.sql이 미리 적용된 별도 로컬 스키마만 사용한다. 테스트가 테이블을 생성/변경하지 않으며 종료 시 UUID 작성자의 자기 fixture만 FK 순서로 정리한다. 운영 OCI DB에 접근하지 않는다.

후속 실행 명령 후보는 `.\gradlew.bat test --tests 'com.taehyung.mes.lot.MaterialConsumptionContractTests' --tests 'com.taehyung.mes.lot.MaterialConsumptionServiceTests' --tests 'com.taehyung.mes.lot.MaterialConsumptionControllerTests' --tests 'com.taehyung.mes.lot.MaterialConsumptionMapperTests'`와, 전용 DB 보호 조건을 설정한 뒤 `.\gradlew.bat test --tests 'com.taehyung.mes.lot.MaterialConsumptionMysqlIntegrationTests'`다. 실행은 목적/범위를 먼저 안내하고 개발자의 명시적 요청 후 수행한다.

## 정합성과 다음 단계

BR-02·BR-13·PRD FR-07 및 기존 DDL의 소비/Receipt UNIQUE·FK·양수 CHECK·가용량 CHECK를 유지한다. 이번 등록에 필요한 미확정 정책은 없다. 물리 DDL에 이미 있는 MATERIAL_RETURN 및 반환 receipt_id를 설계 문서에 동기화했으나 반환 실행 계약/기능은 구현하지 않았다.

다음 반환 단계는 Receipt → WorkOrder → 원소비 → RawLot 순서, 원소비별 최신 반환합·순소비 상한, 조건부 복원과 등록수량 상한, 원장/재고/Receipt의 원자성, 반환 사유·요청 payload·재응답 계약을 함께 확정해야 한다. 예약·임의 조정·소비 기록 오류 교정은 별도 정책이다. 기존 최초 소비 snapshot을 현재 잔고나 반환 후 수량으로 덮어쓰면 안 된다.
