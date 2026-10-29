# Material Return Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. 개발자 승인에 따라 같은 세션에서 구현한다.

**Goal:** 원소비와 연결된 반환 및 원자적 재고 복원.
**Architecture:** 승인된 Receipt 계약, 별도 반환 TransactionService와 MyBatis Mapper, 기존 관리자 화면.
**Tech Stack:** Java 17, Spring MVC, MyBatis, MySQL, Thymeleaf, JUnit 5.
**Spec:** docs/superpowers/specs/2026-10-28-material-return-design.md

## Global Constraints
- schema.sql 변경·운영 DB 접근 금지. 기존 소비 저장 계약 보존.
- 실제 MySQL 실행은 별도 로컬 스키마와 명시적 승인 필요.
- 생산실적·완제품 LOT·품질·임의 재고 조정 제외.

## Review Focus
- reason의 공백/Unicode/개행과 fingerprint 원문 일치.
- 재전송 시 현재 상태가 완료여도 최초 성공 재사용.
- 원소비 잠금 추가로 RawLot 이후 역방향 잠금이 생기지 않음.
- 복원 범위 초과·0 영향행수·성공 JSON 불일치 전체 롤백.
- 변조된 작업지시/LOT 입력이 서버 결정값에 영향을 주지 않음.

### Task 1: 입력·저장 계약
Files: lot/MaterialReturn{Form,Command,Service,TransactionService,Mapper,Outcome,OutcomeCodec,Receipt,RuleException,NotFoundException,RequestExistsException}.java, MaterialReturn.java, MaterialReturnMapper.xml, MaterialReturnContractTests.java, MaterialReturnServiceTests.java.
Interfaces: create(MaterialReturnForm) → MaterialReturnOutcome, get(Long) → MaterialReturn, source(Long) → MaterialConsumption, list(String,String,String,String) → List<MaterialReturn>.
- [x] 계약 테스트: 0/음수/소수7자리/범위/UUID/사유 거부, 1과1.000000 동일 fingerprint, 사유/대상/수량 변경은 다름.
- [x] RED 실행 후 승인된 잠금/한도/복원/Receipt 구현.
- [x] Mock 검증: 소비60/반환20/신규30→가용60에서90, 신규41 거부; 상태/단위/영향행수/rollback/replay 검사.

### Task 2: 화면
Files: MaterialReturnController.java, lot/return/{list,form,detail}.html, layout.html, 기존 소비 detail.html; Controller/Template tests.
Interfaces: 승인된 4 URL, 허용 입력 4필드. 원소비별 조회는 consumptionId 검색 사용.
- [x] 변조 입력·오류 입력/키 보존·완료 이력 조회 테스트 작성.
- [x] 기존 CSS/class·formatter로 화면 연결. 브라우저/서버 실행하지 않음.
- [x] DB 미접속 MockMvc/Thymeleaf 렌더 테스트.

### Task 3: MySQL 증명·문서·최종 검토
Files: MaterialReturnMysqlIntegrationTests.java, business-rules.md, database-design.md, erd-design.md, mini_mes_prd.md, 반환 검증 기록.
- [x] 127.0.0.1/*_return_test 및 환경변수 gate, fixture만 정리.
- [x] 정상·한도·동시성·동일키·소비 경합·쓰기 단계별 rollback·원본 불변·집계/원장 검증 테스트 작성.
- [x] 정책 문서에 9차 승인 계약 반영.
- [x] DB 미접속 관련 회귀 테스트 실행. MySQL 실행은 보류.
- [x] 별도 최종 코드 리뷰 및 중요한 지적 수정. 커밋/PR은 요청 범위에 없음.

## 실행 결과

반환 및 관련 기존 흐름의 DB 미접속 회귀 83건 통과. MySQL 테스트 소스 컴파일 완료, 실제 DB 실행은 보류. 독립 검토 에이전트가 사용량 제한으로 실패하여 작성자 정적 검토로 마무리했다. 관련 증거와 미검증 범위는 docs/material-return-verification.md에 기록했다. 커밋/PR은 생성하지 않았다.
