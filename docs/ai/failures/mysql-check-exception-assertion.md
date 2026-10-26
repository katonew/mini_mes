# AI Failure - MySQL CHECK 예외 검증 타입

## AI Suggestion

생산계획 MySQL 통합 테스트에서 계획 INSERT 이후 음수 수량 UPDATE로 CHECK 위반을 유발하고 DataIntegrityViolationException 발생을 기대하도록 작성했다.

## Problem

CHECK 오류 3819의 SQLState는 HY000이다. 읽기 전용 리뷰에서 로컬 Connector/J 및 Spring JDBC의 기본 예외 번역 경로를 대조한 결과 UncategorizedSQLException으로 전달되는 경로가 확인되었다. 특정 하위 예외 타입을 고정하면 실제 DB 롤백이 수행되어도 검증이 실패할 수 있다.

## Risk

예외 타입 검증에서 중단되어 뒤의 저장 건수/롤백 검증에 도달하지 못하고 실제 코드 결함이 없는 상황을 테스트 실패로 보고할 수 있다. 운영 데이터 변경과 관련된 오류는 아니다.

## Verification

실제 테스트나 DB는 실행하지 않았다. 독립 정적 리뷰의 Connector/J 9.7.0 및 Spring JDBC 6.2.19 소스 대조 결과를 근거로 확인했다. 런타임 재현 결과로 기록하지 않는다.

## Fix

ProductionPlanMysqlIntegrationTests.java에서 DataAccessException 상위 타입을 확인하고 원인 체인의 SQLException 오류 코드가 3819인지 검증하도록 수정했다. 이후 실제 저장 건수가 0인지 검사하는 기존 롤백 검증을 유지했다. 수정 후 테스트 역시 실행하지 않았다.
