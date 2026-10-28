# 원자재 LOT 등록·조회 구현 및 검증 안내

## 화면과 URL

| 기능 | URL |
|---|---|
| LOT 번호·원자재 코드·명칭 검색 | GET /raw-material-lots |
| 등록 입력 | GET /raw-material-lots/new |
| 신규 등록 | POST /raw-material-lots |
| 기본정보·소비·반환·순소비·정합성 조회 | GET /raw-material-lots/{lotId} |

RawMaterialLotController → RawMaterialLotService → RawMaterialLotMapper/XML이며 lot 도메인 패키지에 추가했다. 활성 RAW 후보와 현재 상태 검증을 구분하고, 기존 ItemMapper.findByIdForUpdate·ActorProvider·KstDateTimeFormatter 및 공통 UI를 재사용한다.

## 승인 정책과 DB 정합성

- BR-12 / PRD FR-07: 초기 수량 양수, 원자재 LOT 번호 전역 유일, registered_at 서버 UTC 자동 기록. 2026-10-27 최종 승인 기준이다.
- 기존 uq_raw_lot_no(lot_no), item FK, 수량 비음수/상한 CHECK와 DECIMAL(18,6)을 그대로 사용한다. 신규 양수·EA 정수·소수 6자리 제한은 Service가 저장 전에 검증한다. 입력 scale>6은 끝자리 0도 포함해 거부한다.
- LOT 번호는 표시 가능한 ASCII 1~64자이며 공백만인 값은 거부한다. 자동 정규화하지 않으며 DB collation의 비교 규칙을 그대로 사용한다.
- 신규 LOT의 보존 단위는 잠긴 품목 단위다. registered_qty와 available_qty는 같은 매개변수로 한 INSERT에 저장한다.
- registered_at=UTC_TIMESTAMP(6), created_at은 기존 CURRENT_TIMESTAMP(6) 기본값을 유지한다. 기존 연결 세션 UTC 설정과 같은 규약이다. 별도 시각 컬럼의 의미·마이크로초 정밀도를 보존한다.
- 화면만 공통 formatter로 UTC→Asia/Seoul 변환한다. 상세에서 두 시각은 소수 6자리까지 표시하며 UTC 조회값을 덮어쓰지 않는다.
- create는 단일 트랜잭션에서 Item 행 잠금 → 현재 활성 RAW/단위 확인 → 전역 중복 조회 → INSERT를 수행한다. 품목 수정도 같은 행 잠금을 사용하므로 먼저 비활성화가 커밋되면 등록은 거부한다. 등록이 먼저 완료되면 이후 품목 비활성화가 기존 LOT를 삭제하거나 무효화하지 않는다.
- 서로 다른 품목에서 동일 번호 등록이 경합하면 사전 조회가 모두 통과할 수 있다. 최종 DB UNIQUE 위반을 LOT 번호 업무 오류로 변환하고 전체 트랜잭션을 롤백한다.
- request_receipt의 허용 operation에는 LOT 생성이 없으므로 새 operation·Receipt 연결을 만들지 않았다. 같은 LOT 번호의 재전송은 중복 오류이며 별도 성공 재응답 계약을 주장하지 않는다. 저장 결과가 불명확하면 번호로 목록을 확인한다.
- 상세는 소비합과 반환합을 별도 상관 서브쿼리로 같은 SELECT에서 읽어 행 곱을 피한다. 소비·반환 데이터 생성은 하지 않는다. 가용수량과 등록량−소비+반환이 다르거나 반환합이 소비합보다 크면 경고만 표시한다. 이는 LOT 단위 대조이며 원소비별 반환 상한 검증을 대체하지 않는다.
- DB 스키마·설정, 기존 업무 Service/Mapper, 공통 CSS·JavaScript는 수정하지 않았다. DB 설계의 과거 미확정 표기는 이번 원자재 LOT에 한해 BR-12의 승인으로 해소하며 나머지 정책을 확정하지 않는다.

## 파일

- Java: RawMaterialLot.java, RawMaterialLotForm.java, RawMaterialLotMapper.java, RawMaterialLotService.java, RawMaterialLotController.java, RawMaterialLotRuleException.java, RawMaterialLotNotFoundException.java.
- SQL/UI: RawMaterialLotMapper.xml, 원자재 LOT용 list.html/form.html/detail.html.
- 공통: layout.html, error.html, PageExceptionHandler.java, messages.properties.
- 테스트: RawMaterialLotServiceTests.java, RawMaterialLotControllerTests.java, RawMaterialLotMapperTests.java, RawMaterialLotMysqlIntegrationTests.java.
- 문서: mini_mes_prd.md, business-rules.md, 2026-10-27-raw-material-lot.md, raw-material-lot-verification.md.

## 작성된 테스트와 실행 여부

- Service/Mock: 정상 등록, 양수/정밀도/범위·EA/M/G, 미존재/미사용/비RAW/지원하지 않는 단위, LOT 번호 필수·길이·ASCII, 전역 중복 사전 조회와 DB 중복 예외, 작성자 누락, 초기 수량 일치, 검색·상세·404·수량 불일치, 쓰기 실패 및 Spring 프록시 rollback 호출.
- Controller/standalone MockMvc: URL/모델, 정상 리다이렉트, 서버 관리 필드 바인딩 억제, 등록일시 수정 POST 부재, 입력 오류/DB 장애 시 입력 보존 및 안전한 404/503. HTML 렌더링·브라우저 동작 검증은 아니다.
- Mapper/UI 계약: 원자재 LOT INSERT만 존재, 두 초기수량 동일 매개변수, UTC_TIMESTAMP(6), 전역 중복 조건, 소비/반환 독립 집계, KST formatter 연결과 날짜 경계 변환.
- 실제 MySQL 테스트 코드: 등록과 UTC 업무시각, M/G 정밀도, 같은/다른 품목 중복, 양쪽 사전 조회가 통과한 동시 중복에서 DB UNIQUE 한 건 보호, INSERT 후 실제 CHECK 실패 전체 rollback, 비활성화 잠금 경합, 2건 소비와 한 소비의 2건 반환을 통한 집계 중복 방지·불일치 비보정.
- Mock rollback 테스트는 TransactionManager 호출을 확인하며 실제 DB rollback을 증명하지 않는다. 실제 DB 검증은 별도 통합 테스트가 담당한다.
- **이번 작업에서 테스트·빌드·서버·브라우저·MySQL 연결·DDL은 실행하지 않았다. 정적 검토만 수행했으며 테스트 성공을 주장하지 않는다.**
- 정적 확인: Mapper XML을 외부 DTD 접근 없이 파싱했고, 변경된 기존 파일의 git diff --check에서 공백 오류가 없었다. SQL 컬럼·FK·UNIQUE·CHECK를 기존 DDL과 대조했으며 schema.sql·기존 업무 Service/Mapper·시간 설정·공통 CSS/JavaScript의 diff는 없었다. Java 컴파일·Thymeleaf 렌더링 성공은 미확인이다.

향후 실행 시 먼저 개발자에게 명령·목적·범위를 안내하고 실행 요청을 받아야 한다. 일반 테스트 후보 명령은 `.\gradlew.bat test --tests 'com.taehyung.mes.lot.RawMaterialLotServiceTests' --tests 'com.taehyung.mes.lot.RawMaterialLotControllerTests' --tests 'com.taehyung.mes.lot.RawMaterialLotMapperTests'`이다.

MySQL 테스트는 운영 OCI DB를 사용하지 않는다. `MES_RAW_LOT_MYSQL_TEST=LOCAL_TEST_ONLY`와 `RAW_LOT_TEST_DB_NAME`(`*_raw_lot_test`), `RAW_LOT_TEST_DB_USER`, `RAW_LOT_TEST_DB_PASSWORD`가 모두 필요하다. 포트만 `RAW_LOT_TEST_DB_PORT`로 지정하며 호스트는 127.0.0.1 고정이다. application.yml과 일반 DB_*는 읽지 않는다. 개발자가 사전에 기존 schema.sql을 적용한 독립 로컬 스키마만 사용하고 테스트가 테이블을 생성·변경하지 않는다. 테스트 종료 시 UUID 작성자로 식별한 자기 fixture만 FK 순서로 정리한다. 명령 후보는 `.\gradlew.bat test --tests 'com.taehyung.mes.lot.RawMaterialLotMysqlIntegrationTests'`이다.

## 다음 단계

신규 등록·조회에 필요한 미확정 정책은 없다. 원자재 소비·반환에서는 WorkOrder → 원소비(반환 시) → RawLot 잠금 순서, 조건부 가용수량 차감/복원, Receipt operation·요청 키·payload 계약, 원소비별 반환 상한, 원장과 재고의 원자 저장을 함께 구현해야 한다. 예약·조정·입고 추가·폐기·창고 이동·소비 기록 오류 교정은 별도 업무 결정이 필요하다.
