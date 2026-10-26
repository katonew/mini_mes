# 생산계획 구현 계획

**근거/범위:** 사용자 5차 요청, FR-05/06, production_plan/work_order DDL. 기존 공정·Routing 미커밋 변경 유지. production 패키지에 Controller → Service → Mapper XML 구조를 추가한다.

**확인 사항:** 계획코드/상태/코드 UNIQUE가 없으므로 추가하지 않는다. PK 계획 ID 검색을 제공한다. 수정·취소는 미확정이므로 등록·목록·상세만 구현한다. BOM/Routing 참조는 작업지시에서 확정하며 계획에서 고정하지 않는다. 모든 품목의 계획수량에 적용되는 기존 CHECK에 따라 양의 정수 및 DECIMAL(18,6) 범위를 검증한다. 과거 날짜 제한 등 미정 정책은 추가하지 않고 필수/엄격한 날짜 파싱/MySQL DATE 범위만 검증한다.

- [x] ProductionPlanServiceTests.java, ProductionPlanControllerTests.java, ProductionPlanMapperTests.java 작성(미실행). 코드 중복 테스트는 컬럼 부재로 추가하지 않는다. 조회·숫자·날짜·품목·작성자·저장 오류·트랜잭션·참조 보호를 포함한다.
- [x] ProductionPlan.java/ProductionPlanForm.java, 예외, Mapper.java/XML, Service.java 추가. 제품 잠금은 기존 ItemMapper 재사용, 저장은 Service 트랜잭션이다.
- [x] Controller 및 목록/등록/상세 Thymeleaf, 메뉴/오류/메시지 연결. 검색은 계획 ID·제품 코드·명칭만 제공한다. 공통 CSS/JS 재사용.
- [x] 별도 opt-in의 로컬 MySQL 통합 테스트 작성(미실행). Mock의 롤백 호출과 실제 DB rollback을 구분한다.
- [x] BR-10 및 PRD/DB/ERD 관련 항목, 검증 안내 반영 및 정적/독립 읽기 전용 리뷰.

**금지:** 빌드·테스트·린트·브라우저·렌더링·개발서버·DB 실행 없음, 운영 OCI 접근 없음. 스키마·의존성 변경, 작업지시·LOT 자동생성·재고차감·실적 등록, 미확정 UPDATE/DELETE/API 추가 없음. 커밋·push 없음.

**리뷰 기록:** 실제 MySQL CHECK 오류를 특정 DataIntegrityViolationException으로 가정한 테스트를 발견했다. DataAccessException 및 원인 SQLException의 3819 오류코드 검증으로 수정하고 실패 기록을 남겼다. 나머지 확정 결함 발견 없음. 수정 후 포함 모든 실행 검증은 미수행이다.
