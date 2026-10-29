# 생산실적·완제품 LOT 구현 및 검증 안내

## 화면과 URL

| 기능 | URL |
|---|---|
| 생산실적 목록·검색 | `GET /production-results` |
| 생산실적 등록 | `GET /production-results/new?workOrderId=`, `POST /production-results` |
| 생산실적 상세 | `GET /production-results/{resultId}` |
| 완제품 LOT 목록·검색 | `GET /finished-goods-lots` |
| 완제품 LOT 상세 | `GET /finished-goods-lots/{lotId}` |
| 작업지시 완료 | `POST /work-orders/{id}/complete` |

## 저장·동시성 규약

- 최초 요청은 사용자 입력 LOT 번호를 포함한 `mode=FIRST`, 후속 요청은 LOT 번호 없이 `mode=EXISTING` fingerprint 계약을 사용한다. 수량은 `stripTrailingZeros().toPlainString()`으로 정규화하고 LOT 번호·작업자 식별자는 원문을 보존한다.
- `Receipt → WorkOrder FOR UPDATE → FinishedGoodsLot 조회/최초 INSERT → ProductionResult INSERT → Receipt 성공 기록` 순서다. WorkOrder 잠금으로 동시 최초 등록, 누적 생산 상한, 완료 요청과의 경합을 직렬화한다.
- 최초 LOT과 실적, 성공 Receipt는 하나의 `REQUIRES_NEW/READ_COMMITTED` 트랜잭션이다. 쓰기 영향행수와 생성 ID를 확인하며 어느 단계든 실패하면 전체 롤백한다.
- 생산시각은 `production_result.produced_at=UTC_TIMESTAMP(6)`이고 화면만 KST로 변환한다. `created_at`, `created_by`, 작업자 `worker_ref`의 의미를 구분한다.
- 완료는 별도 요청이다. WorkOrder 잠금 뒤 현재 실적을 재집계하고 누적 생산=지시수량, 생산=양품+불량을 확인한 다음 누적 양품을 `inspection_target_qty`로 저장하면서 상태·완료시각·처리자를 함께 갱신한다.

## 테스트 구분

- Mock/계약 테스트: `ProductionResultContractTests`, `ProductionResultServiceTests`, `ProductionResultControllerTests`, `ProductionResultMapperTests`, 기존 `WorkOrderServiceTests`의 완료 검증.
- 실제 MySQL 테스트: `ProductionResultMysqlIntegrationTests`. 최초/후속 실적, 동일 LOT, UUID 재전송, payload 불일치, 누적 초과, 중복 LOT, 동시 최초 등록, 실적/Receipt 실패 롤백, 완료 및 완료-등록 경합을 검증한다.
- 통합 테스트는 기본 비활성이다. 로컬 `127.0.0.1:3306`의 별도 `mini_mes_result_test` 스키마에 기존 `schema.sql`을 사전 적용해야 한다. 운영 OCI와 개발 DB 설정은 읽지 않는다.
- 실행 조건: `MES_RESULT_MYSQL_TEST=LOCAL_TEST_ONLY`, `RESULT_TEST_DB_NAME=mini_mes_result_test`, `RESULT_TEST_DB_USER`, `RESULT_TEST_DB_PASSWORD`가 필요하다. 테스트는 UUID 작성자의 fixture만 FK 역순으로 삭제하며 스키마를 DROP/TRUNCATE하지 않는다.

## 실행 여부

코드 작성 단계에서는 빌드·Mock 테스트·MySQL 테스트를 실행하지 않았다. AGENTS.md에 따라 실행 명령과 범위를 승인받은 후 별도로 검증한다.
