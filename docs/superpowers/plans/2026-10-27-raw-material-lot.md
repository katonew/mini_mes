# 원자재 LOT 등록·조회 구현 계획

**Goal:** 승인된 양수 초기수량·전역 LOT 번호·서버 UTC 등록시각 정책으로 등록·목록·상세를 제공한다.
**Architecture:** 기존 Controller → Service → MyBatis 패턴과 lot 도메인 패키지, ItemMapper 잠금, ActorProvider, KST formatter를 재사용한다.
**Spec:** 2026-10-27 개발자 최종 승인 및 PRD FR-07. DB 스키마 변경, 소비·반환 생성, 재고 조정은 제외한다.

- [x] Service/Controller/Mapper 계약 테스트 작성: 양수·단위·정밀도·범위·전역 중복·서버 관리값 바인딩 차단·404·오류 입력 보존.
- [x] RawMaterialLot/Form/Controller/Service/Mapper/XML 구현. create는 item FOR UPDATE → 활성 RAW/단위 확인 → 중복 조회 → 단일 INSERT. registered_at=UTC_TIMESTAMP(6), available_qty=registered_qty.
- [x] 목록/등록/상세 및 공통 메뉴·오류 연결. 상세 소비·반환은 독립 집계하고 불일치 시 경고만 표시. 기존 KST formatter 사용.
- [x] 로컬 전용 *_raw_lot_test 스키마와 명시적 opt-in을 요구하는 MySQL 테스트 작성: 중복 동시성·실제 rollback·비활성화 경합·집계.
- [x] PRD/업무 규칙에 원자재 LOT 정책만 반영하고 검증 안내 작성. 정적 검토 후 실행 여부를 구분해 보고.

테스트·빌드·서버·브라우저·DDL·DB 연결은 이번 작업에서 실행하지 않는다. 사용자 승인에 따라 추가 승인 없이 직접 구현한다.
