# 9차 원자재 반환 구현·검증 기록

2026-10-28. 정책: BR-14, database-design.md 10.4.1절. 기존 스키마 변경 없음.

## 화면과 URL
- GET /material-returns: 작업지시·원소비·LOT·원자재 검색.
- GET /material-returns/new?consumptionId=: 원소비·기반환·반환가능수량과 같은 LOT 표시.
- POST /material-returns: 반환 등록, 성공/동일 요청 재응답은 반환 상세로 이동.
- GET /material-returns/{returnId}: 완료 상태를 포함한 과거 반환, 최초 재고 전후와 현재 가용량 구분.
- 기존 소비 상세에 반환 등록 및 원소비별 반환 이력 링크 추가, 기존 관리자 메뉴 연결.

## 변경 파일
- 신규 Java: MaterialReturn.java, MaterialReturnForm.java, MaterialReturnCommand.java, MaterialReturnController.java, MaterialReturnService.java, MaterialReturnTransactionService.java, MaterialReturnMapper.java, MaterialReturnReceipt.java, MaterialReturnOutcome.java, MaterialReturnOutcomeCodec.java, MaterialReturnRuleException.java, MaterialReturnNotFoundException.java, MaterialReturnRequestExistsException.java.
- 신규 SQL/화면: MaterialReturnMapper.xml, 반환 list.html/form.html/detail.html.
- 기존 연결: PageExceptionHandler.java, layout.html, 소비 detail.html.
- 테스트: MaterialReturnContractTests.java, MaterialReturnServiceTests.java, MaterialReturnControllerTests.java, MaterialReturnMapperTests.java, MaterialReturnMysqlIntegrationTests.java.
- 정책: business-rules.md, database-design.md, erd-design.md, mini_mes_prd.md. 설계/계획 및 이 검증 기록 추가.
- 기존 소비 저장 코드·RawMaterialLot 저장 코드·WorkOrder 상태 변경 코드·schema.sql은 수정하지 않음. 작업 전 존재한 AGENTS.md 변경은 보존.

## 정합성 및 잠금 검토
- 신규 반환은 IN_PROGRESS, 원소비 참조 필수. 소비 원본 UPDATE/DELETE 없음.
- 양수/EA 정수/M·G 소수6자리/DECIMAL(18,6) 검증. 반올림·절삭·환산 없음.
- Receipt→WorkOrder→기존 원소비→RawLot 순서, 부모 식별 비잠금 조회 후 관계 재검증. FOR UPDATE는 단일 테이블이며 BOM/품목 편집 잠금 추가 없음.
- READ_COMMITTED에서 부모 잠금 후 반환합 재계산. 반환합+신규 ≤ 원소비. 가용수량은 registered_qty와 DECIMAL 상한을 Service 및 조건부 SQL로 보호.
- 복원·반환·성공 Receipt 동일 REQUIRES_NEW 트랜잭션, 영향행수/생성 PK/저장 결과/JSON 및 결과 종류 1건 확인. 잠금 실패 포함 예외 시 전체 롤백.
- 기존 소비는 RawLot 이후 기존 원소비 잠금을 요구하지 않으므로 확인한 경로에 직접 역전 없음. 실제 DB 경합/교착 동작은 미실행이므로 검증 완료로 주장하지 않음.
- 순소비 및 LOT 원장은 소비와 반환을 독립 집계. 생산실적/완제품 LOT/품질 생성·수정 없음.

## 중복 요청 계약
기존 MATERIAL_RETURN operation·Receipt UK·반환 receipt FK/UK 사용. 작성자 SHA-256 범위와 UUID, 원소비/정규화수량/원문사유 v1 fingerprint. 같은 키·같은 내용은 최초 결과, 다른 내용 거부. 충돌 시 쓰기 트랜잭션 롤백 후 별도 새 읽기 트랜잭션으로 재조회한다. 완료 이후에도 기존 성공 재전송은 재고를 다시 복원하지 않는다. 다른 UUID의 동일한 물리 반환 중복 입력은 별도 요청이며 물리 사건 자체 식별은 보장하지 않는다.

## 실행한 검증
DB 미접속 14개 테스트 클래스 83건 통과(반환 21건 포함). 반환 계약/Service/Controller/MyBatis 파싱과 기존 소비·RawLot·WorkOrder 회귀 범위다. Gradle compileTestJava로 MySQL 테스트 소스도 컴파일했다. git diff --check 통과.

## 실행하지 않은 검증
실제 MySQL 통합 테스트, 브라우저/개발 서버/화면 시각 검증, 전체 애플리케이션 테스트는 실행하지 않았다. 운영 OCI DB에 접속하지 않았다. 별도 검토 에이전트는 사용량 제한으로 실패하여 독립 리뷰가 아닌 작성자 정적 검토를 수행했다.

## MySQL 통합 테스트 실행 조건
개발자의 명시적 실행 승인 후, 기존 schema.sql을 사전 적용한 로컬 전용 *_return_test 스키마에서만 실행한다. 운영 스키마 변경/접속 및 자동 스키마 생성 없음.

필수 환경변수: MES_RETURN_MYSQL_TEST=LOCAL_TEST_ONLY, RETURN_TEST_DB_NAME, RETURN_TEST_DB_USER, RETURN_TEST_DB_PASSWORD. 선택 RETURN_TEST_DB_PORT(기본 3306). 접속 호스트는 127.0.0.1로 고정. 일반 DB_*는 접속에 사용하지 않는다. 사용자 권한은 해당 로컬 테스트 스키마로 제한한다.

실행 명령: `.\gradlew.bat test --tests "*MaterialReturnMysqlIntegrationTests" --rerun-tasks --console=plain`

작성 사례: 정상 복원/초과·누적상한/0·음수·정밀도/EA·M·G/READY·COMPLETED·CANCELLED/동일 원소비 동시30+20/동일 UUID 경합/같은·다른 지시의 소비 경합/복원·반환·Receipt 후 실패/DB CHECK 실패/0행·잘못된 결과수/실제 잠금 대기 timeout/대기 후 최신 상태/원소비 전체 컬럼 불변/소비·반환·순소비/가용량 원장 대조. 대상 변조는 DB 미접속 Controller 테스트로 별도 검증한다. 실제 InnoDB 교착상태 재현 테스트는 없으며 예외 시 롤백 계약은 Mock 프록시에서 검사한다.

## 추가 결정
현재 승인 범위의 미확정 업무 정책은 없다. 실제 DB 경합·롤백 증명은 위 로컬 통합 테스트 승인 및 실행 후에만 확정할 수 있다.
