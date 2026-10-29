# 원자재 반환 설계

2026-10-28 개발자 승인: 기존 Receipt를 반환에도 적용한다. 기존 소비 저장 계약과 schema.sql은 변경하지 않는다.

## 목적과 범위
실제 반환을 material_return으로 보존하고 같은 원소비 LOT의 가용수량을 복원한다. 소비 원본 UPDATE/DELETE, 재고 조정, 생산실적·완제품 LOT·품질 기능은 제외한다. 기존 Spring MVC/Service/MyBatis/Thymeleaf 흐름을 따른다.

## 대안과 선택
기존 Receipt 사용은 현재 MATERIAL_RETURN CHECK와 FK/UNIQUE로 구현 가능하다. 반환 전용 요청 구조는 같은 책임을 중복하고 DDL 변경이 필요하므로 채택하지 않는다.

## 승인 계약
- scope: 원본 MES_ACTOR_ID UTF-8 SHA-256 소문자 hex. operation: MATERIAL_RETURN 서버 상수.
- 신규 GET UUID, 오류·재전송 때 유지. UUID 소문자 정규화. 같은 scope/key/payload는 최초 성공 상세로 이동, 다른 payload는 거부. 새 키는 독립 요청으로 한도 재검증.
- fingerprint: UTF-8 SHA-256 of `MATERIAL_RETURN:v1\nconsumptionId=<ID>\nreturnedQty=<stripTrailingZeros().toPlainString()>\nreason=<원문 사유>`. LF, 마지막 추가 개행 없음. 사유는 공백 불가·Unicode code point 500자 이하. 사유 원문은 해시와 저장에 동일 사용한다.
- 입력은 consumptionId, returnedQty, reason, requestKey만 허용. 작업지시·LOT·품목·단위는 원소비 경로로 결정. 수량 양수·DECIMAL(18,6), EA 정수, M/G 소수 6자리; 정규화 전 scale 검사. 반올림/절삭/환산 금지.
- 최초 JSON v1: 반환/원소비/작업지시/LOT ID, 반환수량, 단위, 사유, UTC 반환시각, 당시 재고 전후값. 현재 재고와 구별한다. Receipt 종류별 결과 합계 정확히 1건 검증.

## 트랜잭션과 동시성
별도 TransactionService Bean의 REQUIRES_NEW/READ_COMMITTED. Receipt → WorkOrder → 기존 원소비 → RawLot. 원소비의 작업지시 ID를 먼저 비잠금 조회하고 잠금 후 동일 관계를 재검증한다. 단일 테이블 SELECT FOR UPDATE로 조인에 의한 추가 잠금을 피한다. 부모 잠금 후 반환합을 최신 재계산한다. 누적 반환 ≤ 원소비, 복원 후 가용량 ≤ registered_qty 및 DECIMAL 최대값. SQL 조건부 복원, 모든 INSERT/UPDATE 1행 확인. 실패·잠금 대기·교착은 Receipt/반환/재고 모두 롤백, 자동 부분 재시도 없음.

기존 소비는 Receipt→WorkOrder→RawLot 후 신규 소비 INSERT이며 기존 원소비 잠금을 요구하지 않는다. RawLot→기존 원소비의 역방향 잠금을 추가하지 않는다. 같은 LOT 다른 지시는 RawLot에서 직렬화된다. Receipt UNIQUE 충돌만 프록시 롤백 후 별도 읽기 트랜잭션에서 최초 결과 재조회한다. 완료 이후 신규 반환 금지, 기존 이력 조회 및 성공 재전송 허용.

## 화면과 검증
GET /material-returns, GET /material-returns/new?consumptionId=, POST /material-returns, GET /material-returns/{returnId}. 소비 상세에서 등록 및 원소비별 이력 이동. 조회 시 소비·반환 독립 집계, 순소비/반환가능량 표시. 기존 디자인·KST formatter 사용.

Mock 계약/Service/Controller/템플릿 테스트와 실제 MySQL 테스트 분리. MySQL은 127.0.0.1 전용 *_return_test 스키마와 MES_RETURN_MYSQL_TEST=LOCAL_TEST_ONLY 명시적 승인 때만 실행. 운영 OCI 접근 금지. 동시 반환·소비 경합·롤백·원본 불변·원장 대조는 실제 DB 테스트로 검증한다.
