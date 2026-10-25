# Mini MES MySQL 물리 DB 설계

## 1. 설계 개요

이 문서는 승인된 업무 규칙과 `erd-design.md`를 MySQL에서 구현 가능한 테이블·제약·트랜잭션으로 구체화한 추천 설계안이다. 기준 문서는 `AGENTS.md`, `mini_mes_prd.md`, `business-rules.md`, `erd-design.md`다. 기존 문서는 수정하지 않는다.

산업용 온도 센서 모듈의 작업지시·실적·원자재 소비·반환·최종 검사·LOT 역추적을 대상으로 한다. 코드·DDL 실행·DB 생성·migration 파일 작성은 하지 않는다. 개별 제품 시리얼·물리적 중복 검사 식별·공정별 실적·재공품 LOT·병렬 공정은 포함하지 않는다.

표의 자료형·컬럼·인덱스는 물리 추천안이다. 승인된 업무 정책은 유지하며 미확정 업무 정책은 '설계 결정 필요'로 표시한다. 특히 LOT 번호 유일성 범위, 개별 행 0수량 허용, 원자재 기록 오류 정정, 불량 상세 합계 정책을 임의 확정하지 않는다. 이 문서는 즉시 전체 스키마를 배포해도 된다는 승인 문서가 아니다.

## 2. 사용 DB 및 전제조건

### 2.1 DB·명명·시간

| 항목 | 추천 기준 |
|---|---|
| 제품 | MySQL 8.4 LTS 계열, InnoDB. 정확한 8.4.x 패치는 배포 전 확정·검증 |
| 문자셋 | 일반 설명·명칭은 utf8mb4, 기본 collation은 utf8mb4_0900_ai_ci 추천 |
| 코드·상태·요청 키 | ASCII 식별 코드에 한해 ascii / ascii_bin 명시. 한글 업무 코드 허용은 별도 결정 |
| 이름 | 소문자 snake_case, process 대신 예약어 혼동을 줄이는 manufacturing_process |
| 식별자 | BIGINT UNSIGNED PK, AUTO_INCREMENT. FK도 동일 타입. 번호를 업무수량·채번 순서로 사용하지 않음 |
| 시간 | DATETIME(6), DB 연결·애플리케이션 입력 모두 UTC. 화면에서 Asia/Seoul 변환. DATETIME 자체에 시간대 정보는 없음 |
| 격리수준 | 수량 변경 Service 트랜잭션은 READ COMMITTED 추천. 작업지시 부모 잠금·조건부 변경과 함께 적용 |
| 트랜잭션 | 관련 MyBatis Mapper 호출이 같은 Spring 트랜잭션·DB 연결에 참여. autocommit 단일 호출로 분리하지 않음 |
| 삭제·PK 변경 | 모든 아래 FK는 ON DELETE RESTRICT / ON UPDATE RESTRICT 추천. 이력·기준정보의 연쇄 삭제 없음 |

CHECK가 강제되는 MySQL 8.4를 설계 기준으로 삼는다. 이전 버전을 사용한다면 CHECK 지원·강제, collation, SQL mode를 다시 확인한다. 실제 서버 설정은 아직 검증하지 않았다. CHECK는 NULL 처리와 교차 행 한계가 있으므로 필수 컬럼에 NOT NULL을 함께 정의한다. 다른 테이블·서브쿼리 합계 검증은 Service/트랜잭션에 둔다. [MySQL CHECK 제약](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)

### 2.2 표 읽는 방법과 공통 물리 컬럼

- NULL의 N은 NOT NULL, Y는 NULL 허용이다. 기본값 '없음'은 명시 입력이 필요함을 뜻하며 0·빈 문자열을 자동으로 넣지 않는다.
- PK 기본값 AI는 AUTO_INCREMENT다. FK의 부모 생략 표기는 반드시 아래 FK 절을 따른다.
- 일반 `VARCHAR`는 utf8mb4, `코드` 표시 컬럼은 표에 적힌 ASCII collation 추천을 따른다. ID·요청 키 비교는 대소문자와 공백 정규화를 서버 계약으로 고정한다.
- 각 테이블에는 아래 두 공통 컬럼이 실제로 추가된다. 중복 표기를 줄이기 위한 문서 표기이며 Generic Entity·BaseEntity 도입을 의미하지 않는다.

| 공통 컬럼 | MySQL 타입 | NULL | 기본값 | 의미 |
|---|---|---|---|---|
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 행 기록 시각. produced_at·inspected_at 등 업무 발생시각과 구분 |
| created_by | VARCHAR(64) | N | 없음 | 서버가 결정한 행 작성 주체. 품목의 개발용 작성자 정책은 아래 4.1절, 인증·사용자 FK는 미확정 |

현재 값을 수정하는 테이블에는 필요한 곳만 updated_at을 둔다. 소비·반환·검사·정정 이력·성공 요청 원본은 일반 수정·삭제하지 않는다. 변경 사유와 전후 값을 보존하는 업무 기록을 일반 애플리케이션 로그로 대체하지 않는다.

### 2.3 정확한 수량 타입과 정책 경계

수량 컬럼은 기존 schema.sql의 **DECIMAL(18,6)**을 따른다. 정수부 12자리·소수부 6자리다. 품목 화면의 확정 정책은 EA(정수), M·G(소수 허용)이며 단위 자동 환산은 하지 않는다. 향후 수량 Service는 EA 정수성과 소수 정밀도를 저장 전에 검증한다. 업무별 BOM 계산의 반올림 등 추가 정책은 **설계 결정 필요**로 유지한다.

소수 6자리 초과 입력·범위 초과 입력을 DB의 암묵적 변환·절삭에 맡기지 않는다. 서버에서 정확한 수치로 파싱하고 허용 정밀도를 검증한다. SQL strict mode와 경계값 동작은 DDL 검증 단계에서 확인한다. 품목·단위가 다른 수량을 합산하지 않으며 BOM 계산도 구성품별 단위가 일치할 때만 한다. 단위 자동 변환은 추가하지 않는다.

## 3. 테이블 목록

기본안 **18개**, 조건부 불량 상세 후보 **2개**, 총 **20개 명세**다. 조건부 후보는 업무 정책 승인 전 생성 범위로 확정하지 않는다.

| 번호 | 테이블 | 책임·상태 |
|---|---|---|
| 1 | item | 품목 식별·명칭·단위 |
| 2 | bom | BOM 헤더와 생산 품목 |
| 3 | bom_version | 적용 버전·기준 생산량·사용 후 잠금 표시 |
| 4 | bom_component | 버전별 구성 원자재·소요량 |
| 5 | manufacturing_process | 공정 정의 |
| 6 | routing | Routing 헤더·생산 품목 |
| 7 | routing_revision | 적용 Revision·사용 후 잠금 표시 |
| 8 | routing_step | Revision별 공정 순서 |
| 9 | production_plan | 생산계획 |
| 10 | work_order | 지시량·적용 기준·상태·완료 검사대상 |
| 11 | finished_goods_lot | 작업지시당 하나의 완제품 LOT |
| 12 | production_result | 현재 유효 생산실적 |
| 13 | production_result_correction | 실적 정정 전후 이력 |
| 14 | raw_material_lot | 원자재 등록량·저장 가용수량 |
| 15 | material_consumption | 실제 소비 원본 |
| 16 | material_return | 실제 반환, 원래 소비 FK |
| 17 | quality_inspection | LOT별 검사 회차·수량 |
| 18 | request_receipt | 요청 키·payload 검증·성공 결과 재조회 |
| 19 | production_defect | 조건부: 생산 불량 사유 상세 |
| 20 | inspection_defect | 조건부: 검사 불합격 사유 상세 |

별도 lot_trace 복사 테이블·완제품 재고 테이블·개별 제품 시리얼 테이블은 만들지 않는다. 기록 오류 정정 후보 테이블은 13절 비교 대상이며 위 기본안 수에 포함하지 않는다.

## 4. 테이블별 컬럼 명세

아래 모든 표에는 2.2절의 created_at·created_by가 추가된다. 개별 제약명은 설명용이며 최종 DDL에서 스키마 내 중복 없이 명명한다. 'U?'는 업무 유일성 범위 승인 후 적용할 조건부 후보다.

### 4.1 item

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| item_id | BIGINT UNSIGNED | N | AI | PK |
| item_code | VARCHAR(50), 코드 | N | 없음 | 전역 UNIQUE 업무 품목코드, schema.sql에서 승인 반영 |
| item_name | VARCHAR(200) | N | 없음 | 품목명 |
| item_kind | VARCHAR(32), 코드 | N | 없음 | RAW·SEMI·FINISHED. MVP 신규 등록은 RAW·FINISHED만 허용 |
| unit_code | VARCHAR(16), 코드 | N | 없음 | EA·M·G. EA 정수, M·G 소수 허용, 자동 환산 없음 |
| is_active | TINYINT UNSIGNED | N | 1 | 사용 표시. 과거 참조를 삭제하지 않음 |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 현재 속성 갱신 시 Service에서 명시 변경 |

PK item_id. FK 없음. CHECK is_active IN (0,1), 전역 UNIQUE uq_item_code는 기존 schema.sql을 따른다. 관련 업무는 안정적인 PK로 참조한다. 1차 품목 화면에서 확정한 정책에 따라 코드·구분·단위는 등록 후 변경 불가이며 명칭·사용 여부만 수정한다. 물리 삭제 없이 미사용 품목의 기존 참조를 보존한다. 미사용 품목은 향후 신규 BOM 구성·작업지시 대상에서 제외한다.

품목 created_by는 MES_ACTOR_ID의 개발용 시스템 식별자를 Service가 검증해 등록 시에만 기록한다. 누락·공백·64자 초과는 거부하며 LOCAL_DEV는 인증 사용자로 해석하지 않는다. 수정 시 작성자를 변경하지 않는다. 향후 인증 도입은 분리한 ActorProvider의 구현 교체로 연결하고 현재 인증 기능은 추가하지 않는다. 이 보완은 기존 DDL을 변경하지 않는다.

### 4.2 bom

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| bom_id | BIGINT UNSIGNED | N | AI | PK |
| product_item_id | BIGINT UNSIGNED | N | 없음 | item FK, 생산 품목 |
| bom_code | VARCHAR(50), 코드 | N | 없음 | BOM 업무 식별 코드, U? |

PK bom_id, FK product_item_id → item.item_id. 인덱스 ix_bom_product(product_item_id). 헤더 하나당 생산 품목 하나. 품목당 BOM 헤더 하나라는 UK는 정책에 없으므로 추가하지 않는다. CHECK 별도 없음. bom_code 유일성 범위는 승인 후 결정한다.

### 4.3 bom_version

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| bom_version_id | BIGINT UNSIGNED | N | AI | PK |
| bom_id | BIGINT UNSIGNED | N | 없음 | bom FK |
| version_no | INT UNSIGNED | N | 없음 | 헤더 내 버전 식별. 표시명 V1 등은 화면 표현 |
| base_qty | DECIMAL(18,6) | N | 없음 | 구성 소요량의 기준 제품수량 |
| product_unit_code | VARCHAR(16), 코드 | N | 없음 | 버전 적용 시 제품 기준단위 보존 |
| first_used_at | DATETIME(6) | Y | NULL | 최초 작업지시 참조 시각·불변화 표시 |

PK bom_version_id, FK bom_id → bom.bom_id. UK uq_bom_version(bom_id,version_no). CHECK version_no > 0, base_qty > 0. UK가 FK 인덱스를 겸한다. first_used_at 기록·내용 수정 금지는 Service 잠금 규약으로 보호하며 CHECK만으로 사용 이력 불변을 보장한다고 주장하지 않는다.

### 4.4 bom_component

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| bom_component_id | BIGINT UNSIGNED | N | AI | PK |
| bom_version_id | BIGINT UNSIGNED | N | 없음 | bom_version FK |
| material_item_id | BIGINT UNSIGNED | N | 없음 | item FK, 구성 원자재 |
| line_no | INT UNSIGNED | N | 없음 | 버전 내 구성행 번호 |
| required_qty | DECIMAL(18,6) | N | 없음 | base_qty 생산에 필요한 원자재 예상량 |
| material_unit_code | VARCHAR(16), 코드 | N | 없음 | 구성행의 원자재 단위 보존 |

PK bom_component_id. FK bom_version_id → bom_version, material_item_id → item. UK uq_bom_component_line(bom_version_id,line_no), INDEX ix_bom_component_item(material_item_id). CHECK line_no > 0, required_qty > 0. 동일 원자재 구성행을 금지하는 UK(version,item)는 승인되지 않아 추가하지 않는다. 품목별 예상량은 해당 단위의 구성행을 합산한다.

### 4.5 manufacturing_process

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| process_id | BIGINT UNSIGNED | N | AI | PK |
| process_code | VARCHAR(50), 코드 | N | 없음 | 공정코드, U? |
| process_name | VARCHAR(200) | N | 없음 | 공정명 |
| description | VARCHAR(500) | Y | NULL | 공정 설명 |
| is_active | TINYINT UNSIGNED | N | 1 | 사용 여부 |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 갱신시각 |

PK process_id, FK 없음. CHECK is_active IN (0,1). 코드 UK 범위는 승인 후 적용한다. 공정별 실적 저장 기능은 추가하지 않는다.

### 4.6 routing

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| routing_id | BIGINT UNSIGNED | N | AI | PK |
| product_item_id | BIGINT UNSIGNED | N | 없음 | item FK |
| routing_code | VARCHAR(50), 코드 | N | 없음 | Routing 업무 코드, U? |

PK routing_id, FK product_item_id → item. INDEX ix_routing_product(product_item_id). CHECK 별도 없음. 제품당 Routing 1개 제한은 추가하지 않는다.

### 4.7 routing_revision

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| routing_revision_id | BIGINT UNSIGNED | N | AI | PK |
| routing_id | BIGINT UNSIGNED | N | 없음 | routing FK |
| revision_no | INT UNSIGNED | N | 없음 | 헤더 내 Revision 번호 |
| first_used_at | DATETIME(6) | Y | NULL | 최초 사용 시 불변화 표시 |

PK routing_revision_id, FK routing_id → routing. UK uq_routing_revision(routing_id,revision_no), CHECK revision_no > 0. 작업지시 생성·공정 수정이 같은 Revision 부모 잠금 규약을 사용한다.

### 4.8 routing_step

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| routing_step_id | BIGINT UNSIGNED | N | AI | PK |
| routing_revision_id | BIGINT UNSIGNED | N | 없음 | routing_revision FK |
| process_id | BIGINT UNSIGNED | N | 없음 | manufacturing_process FK |
| step_no | INT UNSIGNED | N | 없음 | 해당 Revision의 순서 |
| process_name_snapshot | VARCHAR(200) | N | 없음 | 적용 공정명 보존용 기술 추천 |

PK routing_step_id. FK routing_revision_id → routing_revision, process_id → manufacturing_process. UK uq_routing_step(routing_revision_id,step_no), INDEX ix_routing_step_process(process_id). CHECK step_no > 0. 순서 번호는 정렬 기준이며 반드시 1씩 연속이어야 한다는 정책은 추가하지 않는다. 스냅샷 명칭은 불변 Revision 내용의 일부다.

### 4.9 production_plan

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| production_plan_id | BIGINT UNSIGNED | N | AI | PK |
| product_item_id | BIGINT UNSIGNED | N | 없음 | item FK |
| planned_qty | DECIMAL(18,6) | N | 없음 | 계획수량 |
| planned_date | DATE | N | 없음 | 계획일 |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 갱신시각 |

PK production_plan_id, FK product_item_id → item, INDEX ix_plan_product(product_item_id). CHECK planned_qty >= 0는 최소 비음수 저장 제약 후보이며 0계획 허용 정책은 미확정이다. 추가 상태 enum·계획번호 채번·계획 변경·취소 정책을 임의 추가하지 않는다. 각 작업지시 생성 시 계획·작업지시 제품을 검증하고 이미 연결된 이력이 훼손되지 않도록 한다.

### 4.10 work_order

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| work_order_id | BIGINT UNSIGNED | N | AI | PK |
| production_plan_id | BIGINT UNSIGNED | N | 없음 | production_plan FK |
| product_item_id | BIGINT UNSIGNED | N | 없음 | 적용 생산 품목 item FK |
| bom_version_id | BIGINT UNSIGNED | N | 없음 | 적용 bom_version FK |
| routing_revision_id | BIGINT UNSIGNED | N | 없음 | 적용 routing_revision FK |
| ordered_qty | DECIMAL(18,6) | N | 없음 | 지시수량 |
| product_unit_code | VARCHAR(16), 코드 | N | 없음 | 작업지시 생성 시 제품단위 보존 |
| status | VARCHAR(16), 코드 | N | READY | 승인된 상태 |
| inspection_target_qty | DECIMAL(18,6) | Y | NULL | 생산 완료 시 확정한 누적 생산 양품수량 |
| started_at | DATETIME(6) | Y | NULL | 생산 시작시각 |
| started_by | VARCHAR(64) | Y | NULL | 시작 주체 |
| completed_at | DATETIME(6) | Y | NULL | 완료 시각 |
| completed_by | VARCHAR(64) | Y | NULL | 완료 주체 |
| cancelled_at | DATETIME(6) | Y | NULL | READY 취소시각 |
| cancelled_by | VARCHAR(64) | Y | NULL | 취소 주체 |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 갱신시각 |

PK work_order_id. FK plan→production_plan, product→item, bom_version→bom_version, routing_revision→routing_revision의 각 PK. INDEX ix_wo_plan(production_plan_id), ix_wo_product(product_item_id), ix_wo_bom(bom_version_id), ix_wo_routing(routing_revision_id). 상태 단독 인덱스는 실제 조회 근거가 없어 기본 추가하지 않는다.

CHECK: ordered_qty >= 0, status IN ('READY','IN_PROGRESS','COMPLETED','CANCELLED'). 상태별 조건부 필수는 5절 표를 따른다. ordered_qty > 0의 신규 지시 제한 자체는 별도 업무 확인 필요지만 COMPLETED의 0수량 완료는 승인 정책에 따라 항상 금지한다. 검사대상 0은 전량 생산불량인 정상 완료와 양립할 수 있으므로 >0을 강제하지 않는다.

### 4.11 finished_goods_lot

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| finished_lot_id | BIGINT UNSIGNED | N | AI | PK |
| work_order_id | BIGINT UNSIGNED | N | 없음 | work_order FK, 작업지시당 LOT 하나 |
| lot_no | VARCHAR(64), 코드 | N | 없음 | 완제품 LOT 번호, 유일성 범위 승인 필요 |

PK finished_lot_id, FK work_order_id → work_order. **UK uq_fg_work_order(work_order_id)**. CHECK 별도 없음. lot_no UK 후보는 5절. 생산·양품·불량·검사 합계는 저장하지 않는다. LOT 품질 요약 상태 컬럼도 상태 정책 결정 전에는 추가하지 않는다.

### 4.12 production_result

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| production_result_id | BIGINT UNSIGNED | N | AI | PK |
| finished_lot_id | BIGINT UNSIGNED | N | 없음 | finished_goods_lot FK |
| receipt_id | BIGINT UNSIGNED | N | 없음 | request_receipt FK |
| produced_qty | DECIMAL(18,6) | N | 없음 | 현재 유효 생산수량 |
| good_qty | DECIMAL(18,6) | N | 없음 | 현재 생산 양품수량 |
| defect_qty | DECIMAL(18,6) | N | 없음 | 현재 생산 불량수량 |
| produced_at | DATETIME(6) | N | 없음 | 업무 생산시각 |
| worker_ref | VARCHAR(64) | N | 없음 | 생산 작업자 |
| revision_no | INT UNSIGNED | N | 1 | 정정 동시성·이력 버전 |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 현재값 갱신시각 |

PK production_result_id, FK finished_lot_id → finished_goods_lot, receipt_id → request_receipt. UK uq_result_receipt(receipt_id). INDEX ix_result_lot(finished_lot_id). CHECK produced_qty >= 0, good_qty >= 0, defect_qty >= 0, produced_qty = good_qty + defect_qty, revision_no > 0. 개별 0생산 실적 허용은 미확정이므로 DB 비음수 조건과 Service 정책 결정을 구분한다. work_order_id를 중복 저장하지 않고 LOT을 통해 찾는다.

### 4.13 production_result_correction

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| correction_id | BIGINT UNSIGNED | N | AI | PK |
| production_result_id | BIGINT UNSIGNED | N | 없음 | production_result FK |
| from_revision | INT UNSIGNED | N | 없음 | 정정 전 revision |
| to_revision | INT UNSIGNED | N | 없음 | 정정 후 revision |
| before_produced_qty / after_produced_qty | 각각 DECIMAL(18,6) | N | 없음 | 정정 전후 생산수량, 실제 컬럼 2개 |
| before_good_qty / after_good_qty | 각각 DECIMAL(18,6) | N | 없음 | 정정 전후 양품, 실제 컬럼 2개 |
| before_defect_qty / after_defect_qty | 각각 DECIMAL(18,6) | N | 없음 | 정정 전후 불량, 실제 컬럼 2개 |
| before_produced_at / after_produced_at | 각각 DATETIME(6) | N | 없음 | 생산시각 변경을 포함할 경우의 전후 값 |
| before_worker_ref / after_worker_ref | 각각 VARCHAR(64) | N | 없음 | 작업자 전후 값 |
| reason | VARCHAR(500) | N | 없음 | 정정 사유 |

PK correction_id, FK production_result_id → production_result. UK uq_result_correction(production_result_id,to_revision). CHECK from_revision > 0, to_revision = from_revision + 1, 전후 수량 각각 비음수, before_produced_qty = before_good_qty + before_defect_qty, after도 동일. 시각은 공통 created_at이 정정 기록시각, created_by가 정정 주체다. 현재 Result의 revision 연속성·현재값과 before 일치는 Service 검증이다.

이 테이블은 집계 원천이 아니다. 생산 합계는 현재 production_result만 사용한다. 정정 가능한 필드의 업무 범위가 승인되지 않았다면 시각·작업자 수정까지 자동 허용하지 않는다. 전후 컬럼을 갖는 것과 해당 변경의 권한을 주는 것은 별개다. 불량 상세 2개 후보를 채택한다면 상세 전후 이력의 추가 구조를 14절에서 결정해야 한다.

### 4.14 raw_material_lot

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| raw_lot_id | BIGINT UNSIGNED | N | AI | PK |
| item_id | BIGINT UNSIGNED | N | 없음 | item FK, 원자재 품목 |
| lot_no | VARCHAR(64), 코드 | N | 없음 | 원자재 LOT 번호, U? |
| unit_code | VARCHAR(16), 코드 | N | 없음 | 등록 시 원자재 단위 보존 |
| registered_qty | DECIMAL(18,6) | N | 없음 | 원천 등록량 |
| available_qty | DECIMAL(18,6) | N | 없음 | 소비·반환과 원자 갱신하는 저장 가용량 |
| registered_at | DATETIME(6) | N | 없음 | 원자재 등록 업무시각 |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 가용량 갱신시각 |

PK raw_lot_id, FK item_id → item. INDEX ix_raw_lot_item(item_id). CHECK registered_qty >= 0, available_qty >= 0, available_qty <= registered_qty. 이 상한은 등록량 변경·예약·별도 조정이 없는 기본안의 최소 제약이다. 가용량 = 등록량 - 소비 + 반환의 교차 행 등식은 Service와 원장 대조로 검증한다. 신규 LOT의 available_qty = registered_qty 초기화는 서버가 수행하며 기본값 0으로 누락을 숨기지 않는다.

### 4.15 material_consumption

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| consumption_id | BIGINT UNSIGNED | N | AI | PK |
| work_order_id | BIGINT UNSIGNED | N | 없음 | work_order FK |
| raw_lot_id | BIGINT UNSIGNED | N | 없음 | raw_material_lot FK |
| bom_component_id | BIGINT UNSIGNED | N | 없음 | 적용 bom_component FK |
| receipt_id | BIGINT UNSIGNED | N | 없음 | request_receipt FK |
| consumed_qty | DECIMAL(18,6) | N | 없음 | 실제 소비 확정량 |
| consumed_at | DATETIME(6) | N | 없음 | 투입 업무시각 |

PK consumption_id. FK work_order_id → work_order, raw_lot_id → raw_material_lot, bom_component_id → bom_component, receipt_id → request_receipt. UK uq_consumption_receipt(receipt_id). INDEX ix_consumption_wo_lot(work_order_id,raw_lot_id), ix_consumption_raw_lot(raw_lot_id), ix_consumption_component(bom_component_id). CHECK consumed_qty > 0 추천. 이 원본은 일반 수정·삭제하지 않는다.

적용 BOM 버전 소속 Component인지, LOT 품목·단위가 그 구성과 일치하는지는 Service 검증이다. CHECK·개별 FK가 서로 다른 부모의 품목 일치까지 보장하지 않는다. required_qty로 실제 소비량을 덮어쓰지 않는다.

### 4.16 material_return

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| return_id | BIGINT UNSIGNED | N | AI | PK |
| consumption_id | BIGINT UNSIGNED | N | 없음 | 원래 material_consumption FK |
| returned_qty | DECIMAL(18,6) | N | 없음 | 실제 반환량 |
| returned_at | DATETIME(6) | N | 없음 | 물리 반환 업무시각 |
| reason | VARCHAR(500) | N | 없음 | 실제 반환 사유 |

PK return_id, FK consumption_id → material_consumption. INDEX ix_return_consumption(consumption_id). CHECK returned_qty > 0 추천. LOT·작업지시·품목은 원소비에서 찾고 입력값으로 다른 LOT을 복원하지 않는다. 원소비별 반환합계 상한은 행 잠금과 합계로 검증한다. 기록 오류 정정용 type으로 이 테이블을 재사용하지 않는다.

### 4.17 quality_inspection

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| inspection_id | BIGINT UNSIGNED | N | AI | PK |
| finished_lot_id | BIGINT UNSIGNED | N | 없음 | finished_goods_lot FK |
| receipt_id | BIGINT UNSIGNED | N | 없음 | request_receipt FK. 이번 요청의 검사 재전송 방지 요구 반영 추천 |
| round_no | INT UNSIGNED | N | 없음 | LOT 내 검사 회차 |
| inspected_qty | DECIMAL(18,6) | N | 없음 | 회차 검사수량 |
| accepted_qty | DECIMAL(18,6) | N | 없음 | 회차 합격수량 |
| rejected_qty | DECIMAL(18,6) | N | 없음 | 회차 불합격수량 |
| inspected_at | DATETIME(6) | N | 없음 | 검사 업무시각 |
| inspector_ref | VARCHAR(64) | N | 없음 | 검사자 |
| judgement_reason | VARCHAR(500) | Y | NULL | 판정 설명 |

PK inspection_id, FK finished_lot_id → finished_goods_lot, receipt_id → request_receipt. UK uq_inspection_round(finished_lot_id,round_no), uq_inspection_receipt(receipt_id). CHECK round_no > 0, inspected_qty >= 0, accepted_qty >= 0, rejected_qty >= 0, inspected_qty = accepted_qty + rejected_qty. 개별 0검사 요청은 정책 확인 전 구현하지 않는다.

회차 UK는 같은 회차 번호 중복만, receipt UK는 같은 요청 재전송만 보호한다. 서로 다른 요청·회차의 누적 검사수량 상한은 부모 잠금과 최신 합계로 검증한다. 실물 중복 식별은 지원하지 않는다. PASS/FAIL/MIXED 등의 요약은 7절 추천 계산식으로 표현하고 별도 상태 컬럼은 확정하지 않는다.

### 4.18 request_receipt

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| receipt_id | BIGINT UNSIGNED | N | AI | PK |
| scope_key | VARCHAR(64), 코드 | N | 없음 | 서버가 결정하는 안정적인 호출 주체 범위 |
| operation_code | VARCHAR(32), 코드 | N | 없음 | MATERIAL_CONSUME / PRODUCTION_RESULT / QUALITY_INSPECTION |
| request_key | VARCHAR(64), 코드 | N | 없음 | 호출자가 재전송 시 유지하는 고유 ID |
| payload_fingerprint | BINARY(32) | N | 없음 | 정규화된 업무 payload의 SHA-256 요약 추천 |
| initial_outcome_snapshot | JSON | Y | NULL | 최초 성공 결과의 식별자·수량·revision 등 재응답용 불변 요약 |
| processed_at | DATETIME(6) | Y | NULL | 성공 처리 완료 시각. DB의 정확한 commit 시각을 뜻하지 않음 |

PK receipt_id, UK uq_request_key(scope_key,operation_code,request_key). 세 키는 모두 NOT NULL이며 ascii_bin으로 비교한다. operation CHECK는 위 세 값, 성공 필드 CHECK는 `(processed_at, outcome)` 모두 NULL이거나 모두 NOT NULL인 조합이다. 트랜잭션 내부 선점 단계의 NULL을 허용하지만 성공 결과 없는 Receipt를 커밋하는 것은 Service에서 금지한다. committed PENDING 상태를 별도 도입하지 않는다.

각 업무 테이블의 receipt FK·UK는 유효 Receipt 참조와 종류별 최대 1개를 보장한다. Receipt가 반드시 하나의 결과를 가지는 조건과 세 종류 중 정확히 하나에만 연결되는 조건은 Service 검증이다. 모든 등록 Service는 operation_code와 결과 종류를 일치시킨다. DB CHECK로 다른 세 테이블을 조회할 수 있다고 가정하지 않는다. 10절에서 scope·재시도 계약을 설명한다.

### 4.19 production_defect — 조건부 후보

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| production_defect_id | BIGINT UNSIGNED | N | AI | PK |
| production_result_id | BIGINT UNSIGNED | N | 없음 | production_result FK |
| defect_code | VARCHAR(50), 코드 | N | 없음 | 불량 원인 코드, 코드목록 승인 필요 |
| defect_qty | DECIMAL(18,6) | N | 없음 | 해당 사유 수량 |
| note | VARCHAR(500) | Y | NULL | 설명 |

PK production_defect_id, FK production_result_id → production_result. INDEX ix_prod_defect_result(production_result_id), CHECK defect_qty >= 0 후보. 동일 제품의 복수 사유 허용·상세합과 부모 불량수량 관계가 미확정이므로 UK(result,defect_code) 및 합계 정책을 임의 추가하지 않는다. 승인 전 생성·입력 구현은 보류한다.

### 4.20 inspection_defect — 조건부 후보

| 컬럼 | 타입 | NULL | 기본값 | 업무 의미 |
|---|---|---|---|---|
| inspection_defect_id | BIGINT UNSIGNED | N | AI | PK |
| inspection_id | BIGINT UNSIGNED | N | 없음 | quality_inspection FK |
| defect_code | VARCHAR(50), 코드 | N | 없음 | 검사 불합격 원인 |
| rejected_qty | DECIMAL(18,6) | N | 없음 | 해당 사유 불합격수량 |
| note | VARCHAR(500) | Y | NULL | 설명 |

PK inspection_defect_id, FK inspection_id → quality_inspection. INDEX ix_inspection_defect_parent(inspection_id), CHECK rejected_qty >= 0 후보. 복수 원인 중복 집계·판정 상세 정책 승인 전 보류한다. 생산 불량 상세와 하나의 범용 type/id 테이블로 합치지 않아 FK를 유지한다.

## 5. PK/FK/UNIQUE/CHECK와 보장 범위

### 5.1 주요 제약

| 보장 사항 | DB 제약 | DB만으로 해결되지 않는 조건 |
|---|---|---|
| 작업지시당 LOT 최대 1개 | finished_goods_lot.work_order_id NOT NULL FK + UK | 최초 실적과 LOT 동시 생성, 커밋된 LOT의 실적 존재 |
| 실적 LOT 귀속 | production_result.finished_lot_id FK | LOT에서 유도한 작업지시의 현재 상태·수량 검증 |
| 생산수량 등식 | 실적 행 CHECK + 수량 NOT NULL | 작업지시별 실적 합계와 지시량 비교 |
| 반환 대상 존재 | material_return.consumption_id FK | 누적 반환 ≤ 원소비, IN_PROGRESS, 실제 반환 사실 |
| 검사 회차 번호 | (finished_lot_id,round_no) UK | 누적 검사량 상한, 실물 구별 없음 |
| 요청 중복 반영 방지 | request_receipt 키 UK + 업무 receipt_id UK | payload 일치·재응답·원자적 성공 결과 |
| BOM·Routing 적용 이력 | 버전 식별 UK + work_order FK | 사용된 버전의 내용 변경·단위 불일치 차단 |
| 기준정보 삭제 방지 | RESTRICT FK | 논리 비활성화 정책·변경 허용 범위 |

FK의 부모 컬럼은 모두 PK를 참조한다. 자식 FK 컬럼의 타입·부호를 일치시키고 NOT NULL로 필수 관계를 보호한다. 자식 FK 인덱스는 6절에 명시한다. FK의 존재만으로 부모의 상태나 다른 FK 경로의 품목 일치가 검증되지는 않는다. [MySQL FOREIGN KEY 제약과 인덱스](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)

### 5.2 작업지시 행내 상태 제약 추천

| status | 행내 필수·금지 조건 |
|---|---|
| READY | started/completed/cancelled 시각·주체 모두 NULL, inspection_target_qty NULL |
| IN_PROGRESS | started_at·started_by NOT NULL, completed/cancelled 시각·주체 NULL, 검사대상 NULL |
| COMPLETED | started·completed 시각·주체 NOT NULL, cancelled 시각·주체 NULL, ordered_qty > 0, inspection_target_qty NOT NULL이며 0 이상·ordered_qty 이하 |
| CANCELLED | cancelled_at·cancelled_by NOT NULL, started·completed 시각·주체 NULL, 검사대상 NULL |

최종 DDL의 상태 CHECK는 명시적인 IS NULL / IS NOT NULL을 포함한 위 네 분기 OR식으로 작성해야 한다. SQL의 UNKNOWN 때문에 검증이 통과하지 않도록 NULL 포함식을 단순 수치 비교만으로 작성하지 않는다. 이 제약은 한 행의 현재 모양만 보호한다. READY → IN_PROGRESS → COMPLETED, READY → CANCELLED 전이 자체와 누적 생산수량 일치·실적 존재는 Service의 잠금 안에서 검증한다.

### 5.3 유일성 범위가 미확정인 업무 코드와 LOT 번호

기존 문서는 LOT 번호가 중복되면 안 된다고 하면서 범위는 미확정으로 남겼다. 따라서 물리 설계는 다음 대안을 정확히 남기고 임의 확정하지 않는다.

| 대상 | 대안 1 | 대안 2 | 구현 전 결정 |
|---|---|---|---|
| 원자재 LOT | UNIQUE(lot_no) | UNIQUE(item_id,lot_no) | 전역 또는 품목별 유일성 |
| 완제품 LOT | UNIQUE(lot_no) | 품목별 번호: 별도 제품 키 포함 구조 필요 | 현 LOT에 product_item_id가 없으므로 업무 확정 후 복합 FK로 일치 보장하거나 전역 번호 선택 |
| item 코드 | 기존 schema.sql의 전역 UNIQUE 확정 | 품목 화면에서도 사전 조회와 DB UNIQUE로 보호 | 조직·공장 범위 키를 추가하지 않음 |
| process/bom/routing 코드 | 각각 코드 단독 UK 후보 | 승인된 업무 범위 키 + 코드 UK | 이번 품목 화면 보완에서 타 도메인 정책 변경 없음 |

물리 스키마 확정 전 이 항목을 결정해야 한다. 일단 무제약으로 운영 후 나중에 유일성을 추가하는 방식을 추천하지 않는다. 작업지시당 LOT UK와 버전·회차·요청 키 UK는 이 미결정과 관계없이 필수다.

## 6. 인덱스 설계

아래는 4절 인덱스의 목적이다. PK는 InnoDB 기본 식별 경로, UK는 업무 중복 보호, FK 인덱스는 부모 참조 검사와 조인 지원이다. UK의 선두 컬럼으로 FK가 충족되면 같은 단일 컬럼 인덱스를 다시 추가하지 않는다. 복합 인덱스 뒤쪽 컬럼만으로 조회해야 하면 별도 인덱스가 필요하다.

| 조회 | 접근 경로·인덱스 | 역할 |
|---|---|---|
| 품목별 BOM 버전 | bom(product_item_id) → bom_version(bom_id,version_no) UK | FK·조회, 버전 중복 보호 |
| 버전 구성 | bom_component(bom_version_id,line_no) UK | 순서 조회·버전 FK |
| Routing 공정 | routing_revision(routing_id,revision_no) UK → routing_step(routing_revision_id,step_no) UK | 순서·버전 유일성 |
| 계획별 작업지시 | work_order(production_plan_id) | FK·실제 목록 조회 |
| 작업지시별 실적 | finished_goods_lot(work_order_id) UK → production_result(finished_lot_id) | LOT 1건 찾기·실적 집계 |
| LOT별 검사 | quality_inspection(finished_lot_id,round_no) UK | 검사 회차 목록·상한 집계 |
| 원자재 LOT 소비 내역 | material_consumption(raw_lot_id) | rawlot FK·내역 조회 |
| 작업지시 원자재 소비·역추적 | material_consumption(work_order_id,raw_lot_id) | workorder FK·LOT별 묶음 |
| 원소비별 반환 | material_return(consumption_id) | FK·반환합계·이력 |
| 실적 정정 이력 | production_result_correction(production_result_id,to_revision) UK | 순서·중복 버전 방지 |
| 요청 중복 조회 | request_receipt(scope_key,operation_code,request_key) UK | 정확한 3개 키 조회·동시 키 보호 |

4절에서 각 FK에 대해 UK 선두 또는 명시 인덱스가 있다. raw_lot_id는 consumption의 (work_order_id,raw_lot_id) 뒤쪽이므로 단독 인덱스를 별도로 둔다. quality_inspection의 LOT 단독 인덱스와 회차 UK를 중복 추가하지 않는다. 조회용 시각·상태 복합 인덱스, 전체 커버링 인덱스는 실제 SQL·데이터 규모를 보고 결정한다. EXPLAIN이나 DB 성능 검증은 이번 작업에서 수행하지 않았다.

## 7. 상태·코드·버전 및 이력 보존

### 7.1 상태와 품질 판정

업무 상태는 VARCHAR + CHECK와 Java enum의 일치를 후속 구현 기준으로 추천한다. 실제 Java 코드는 작성하지 않는다. 공통코드 테이블에 모든 상태 정책을 넣거나 Mapper SQL이 상태 전이를 임의 판단하게 하지 않는다.

- WorkOrder: 승인된 READY / IN_PROGRESS / COMPLETED / CANCELLED만 저장.
- RequestReceipt.operation_code: 서버에서 결정하는 기술 작업 종류. 클라이언트가 임의 operation을 바꿔 재전송할 수 없음.
- 품목구분: RAW·SEMI·FINISHED로 확정. 신규 등록은 RAW·FINISHED만 허용하며 SEMI는 향후 확장용이다.
- 불량유형: 아직 코드목록이 승인되지 않아 새로운 업무 상태를 생성하지 않음.

품질검사는 수량을 원천으로 두고 다음 판정을 **조회 계산의 추천안**으로 제시한다. 판정 컬럼을 중복 저장하지 않아 수량과 PASS/FAIL이 불일치하지 않게 한다.

| 대상 | 조건 | 추천 표시 |
|---|---|---|
| 한 검사 회차 | 검사수량 > 0, 불합격 0 | PASS |
| 한 검사 회차 | 검사수량 > 0, 합격 0 | FAIL |
| 한 검사 회차 | 합격 > 0, 불합격 > 0 | MIXED(부분 합격), PASS나 FAIL 하나로 정보 손실 방지 |
| LOT 검사 진행 | 누적 검사 0 / 미검사 > 0 / 미검사 0 | NOT_INSPECTED / IN_PROGRESS / FINISHED 등의 별도 진행 표시 추천 |

검사수량 0, 검사대상 0의 표시 정책은 미확정이므로 자동 PASS를 만들지 않는다. LOT 전체의 최종 품질 요약 상태명·부분 합격의 의미·가용량은 **설계 결정 필요**다. 검사 진행 표시와 작업지시 IN_PROGRESS를 혼동하지 않고 화면에서 각각 구분해야 한다. FAIL 또는 MIXED가 되어도 WorkOrder COMPLETED를 되돌리지 않는다. 위 표시명은 승인된 새 상태 전이가 아니다.

### 7.2 기준정보 버전 보존

공유 불변 BOMVersion·RoutingRevision을 추천한다. 작업지시는 정확한 버전 PK를 참조하고 최신 버전을 다시 찾아 연결하지 않는다. 최초 작업지시 생성 시 BOMVersion → RoutingRevision 순으로 배타 행 잠금을 확보하고 제품·단위·구성을 검증한 뒤 first_used_at을 설정한다. 작업지시 삽입과 같은 트랜잭션에서 처리한다. 한번 사용된 버전의 잠금 표시는 취소 후에도 해제하지 않는 추천안이다.

구성행 추가·변경·삭제도 먼저 해당 BOMVersion 또는 RoutingRevision 부모를 잠그고 first_used_at이 있으면 거부한다. 버전 번호·기준생산량·구성 품목·소요량·단위·공정 순서 등 의미 있는 내용은 새 버전으로 작성한다. 부모를 보지 않고 자식 Mapper를 직접 수정하는 경로를 허용하면 이 설계가 깨진다. 버전 테이블의 first_used_at과 FK만으로 DB가 임의 UPDATE를 막는 것은 아니며, 서비스 경로·계정 권한·리뷰로 통제한다. 트리거를 기본 도입하지 않는다.

버전 자식만 보호하고 bom·routing 헤더의 product_item_id를 다른 품목으로 바꾸면 적용 기준의 제품 의미가 달라진다. 사용 이력이 있는 헤더의 제품 재지정도 거부하는 보호가 필요하다. 헤더 수정과 작업지시 생성이 경쟁할 때는 관련 헤더를 먼저 잠근 뒤 BOMVersion → RoutingRevision 순서를 유지하도록 구체화한다. rawlot의 item_id·unit_code도 소비 원본이 생긴 뒤 일반 변경하지 않으며, 미확정 등록량 정정으로 원장 불일치를 우회하지 않는다.

삭제는 FK RESTRICT로 현재 참조를 보호한다. '한번 사용된 이력' 보호도 Service에서 유지하고, 참조가 일시적으로 사라졌다고 물리 삭제를 허용하지 않는다. 기준정보 발행·활성 기간 정책은 미확정이다.

| 방식 | 장점 | 비용·한계 | 추천 |
|---|---|---|---|
| 공유 불변 버전 | 같은 기준 재사용, 버전별 조회 간단 | 모든 수정 경로가 잠금 규약을 지켜야 함 | 현재 MVP 기본안 |
| 작업지시별 전체 스냅샷 | 원본 변경과 독립, 당시 구성 고정 | BOM·공정 구성을 지시마다 복제, 원본과 사본 책임 구분 필요 | 공유 불변을 보장할 수 없으면 대안 |

단위는 BOM 버전·구성·작업지시·rawlot에 적용 단위 값을 보존하고 소비 전 일치를 검증하는 구조를 추천한다. 품목 단위 변경·변환을 자동 허용하지 않는다. 공정명 스냅샷도 당시 표시용이며 변경 가능한 상세 공정 설명까지 보존할지는 추가 결정한다.

### 7.3 생산실적 이력

현재 production_result와 append-only production_result_correction을 분리한다. 현재값 변경·revision 증가·before/after 이력 저장은 하나의 트랜잭션이다. 최초 값은 최초 이력의 before와 요청 성공 요약으로 확인할 수 있다. 다음 정정의 before는 직전 현재값과 같아야 한다. 중복·오래된 정정은 expected revision 검증으로 거부한다. 정정 이력을 실적합계에 더하지 않는다. 원자재 원장·품질검사와 자동 수량 보정을 연결하지 않는다.

## 8. 수량 저장·집계 전략

### 8.1 원천과 파생값

| 수량 | 원천·계산 | 저장·검증 방법 |
|---|---|---|
| 지시수량 | work_order.ordered_qty | 저장 |
| 누적 생산·양품·불량 | production_result의 각 합계 | LOT·작업지시별 집계. 현재 유효값만 포함 |
| 등록수량 | raw_material_lot.registered_qty | 저장, 변경 정책 미확정 |
| 소비수량 | material_consumption.consumed_qty 합 | 작업지시·rawlot·품목·단위별 집계 |
| 반환수량 | material_return.returned_qty 합 | consumption별 선집계 |
| 순소비 | 소비 - 연결된 반환 | 원소비별 먼저 계산 후 묶음 집계 |
| 원장 가용수량 | 등록량 - LOT 소비합 + LOT 반환합 | 계산값을 저장 available_qty와 대조 |
| 저장 가용수량 | raw_material_lot.available_qty | 조건부 차감·원자 복원을 위한 중복 저장 허용 |
| BOM 예상량 | ordered_qty / base_qty × required_qty | 품목·단위별 계산. 같은 구성 품목 여러 행이면 합산 |
| 실제-예상 차이 | 실제 순소비합 - 지시량 기준 BOM 예상량 | 소비총량·반환·순량·예상·차이를 함께 표시 |
| 전체 검사 대상 | 완료 시 production_result.good_qty 합 | work_order.inspection_target_qty 확정 저장 |
| 검사 합격·불합격 | quality_inspection.accepted/rejected 합 | LOT별 회차 원천 집계 |
| 미검사 | 확정 대상 - 회차 inspected_qty 합 | 계산, 음수 발생은 오류 |
| 전체 부적합 | 생산불량합 + 검사불합격합 | 대상이 겹치지 않는 MVP에서 통계만 계산 |

기본 불변식:

```text
각 실적: produced = good + defect
작업지시: sum(produced) <= ordered
완료: 실적 존재 AND sum(produced) > 0 AND sum(produced) = ordered
완료 검사대상: target = sum(good)
각 검사: inspected = accepted + rejected
LOT 검사: target = uninspected + sum(accepted) + sum(rejected)
소비별: sum(returned) <= consumed
원자재 LOT: available = registered - sum(consumed) + sum(returned)
```

완료 생산 100 / 양품 95 / 불량 5라면 완료 기준은 100, 검사 대상은 95다. 검사 합격 92·불합격 3이면 미검사 0, 전체 부적합 통계 8이며 생산실적 불량은 계속 5다. 검사 합격수량을 완제품 출고 가용량으로 자동 선언하지 않는다.

가용량 산식은 예약·다른 재고조정 거래가 없는 현재 원장 범위의 추천 기본안이다. 예약은 업무적으로 미확정이므로 임의로 예약 테이블·예약량 차감을 도입하지 않는다. 등록량의 사후 정정과 신규 재고조정도 자동 허용하지 않는다.

### 8.2 집계 저장의 선택

누적 생산·검사·반환량은 부모 잠금 후 원천에서 계산하여 중복 저장을 피한다. 가용량은 DB에서 원자적으로 차감할 대상이므로 저장한다. 검사대상은 완료 당시 값이라는 의미를 보존하기 위해 별도 확정값으로 저장한다. 정상 상태에서 완료 후 실적이 불변이므로 현재 양품합과 검사대상도 일치해야 한다.

합계는 행 타입과 다른 큰 중간 결과가 생길 수 있으므로 DB SUM·곱셈·나눗셈 결과 범위를 검토하고 애플리케이션도 정확한 수치를 사용한다. BOM 예상량의 나눗셈 반올림 단위는 업무 결정 전 자동 절삭하지 않는다. 저장량 불일치가 발견되면 로그만 남기고 자동 0 보정하지 않으며 원장 대조·원인 확인 대상으로 처리한다.

## 9. 업무별 트랜잭션

### 9.1 공통 전제와 잠금 순서

수량 업무는 MyBatis를 호출하는 Spring Service 한 트랜잭션으로 처리한다. 행 잠금은 DB 배타 잠금이며 Java synchronized·화면 비활성화로 대체하지 않는다. 추천 순서는 **요청 키 선점 → WorkOrder → 기존 Result 또는 원 Consumption → RawMaterialLot**이다. 동일 종류 여러 행이면 PK 오름차순을 따른다. 성공 재전송의 기존 결과 확인·재응답은 신규 업무 반영 경로와 구분한다.

기준정보 사용·편집은 별도 흐름으로 BOMVersion → RoutingRevision 순서다. 작업지시 생성·계획 변경과 관련한 상위 잠금도 물리 구현 전에 일관된 순서로 확정해야 한다. 기존 생산 업무 중 WorkOrder를 잡은 뒤 적용 버전 편집 잠금을 역순으로 요청하지 않는다. 계획 배분 상한은 미확정이므로 잠금을 근거로 새로운 수량 제한을 추가하지 않는다.

### 9.2 업무별 명세

| 업무 | 포함 테이블·검증 | 잠금 순서·동시 처리 | 원자 변경·실패 롤백 |
|---|---|---|---|
| T1 최초 실적·LOT | receipt, work_order, finished_goods_lot, production_result. IN_PROGRESS, 행내 합계·정밀도·누적상한 검증 | 요청 키 → 작업지시 배타 잠금. 기존 LOT 재조회 후 없을 때 생성. LOT.workorder UK를 최종 보호로 사용 | LOT·첫 실적·요청 결과 하나의 커밋. 실패 시 모두 롤백 |
| T2 추가 실적 | receipt, work_order, 기존 LOT, result. IN_PROGRESS, 최신 누적+신규 ≤ 지시량 | 요청 키 → 작업지시. 별도 LOT 생성 금지, receipt UK | 신규 실적·요청 기록 롤백. 이미 커밋된 기존 LOT·실적 유지 |
| T3 실적 정정 | work_order, result, correction. IN_PROGRESS·expected revision·전후 수량·정정후 합계 검증 | 작업지시 → 현재 Result 배타 잠금. current revision과 요청 revision 불일치 시 거부 | 현재값·revision·전후 이력 원자 변경. 이력 실패 시 전부 롤백. 원자재 변경 없음 |
| T4 실제 소비 | receipt, work_order, bom_component, raw_lot, consumption. IN_PROGRESS·적용 BOM·품목·단위·가용량 검증 | 요청 키 → 작업지시 → rawlot 조건부 차감. 잔량 ≥ 수량인 경우에만 변경, 영향행수 1 확인 | 차감·실제 소비 원본·요청 결과 원자 저장. 0행 또는 후속 실패면 모두 롤백 |
| T5 실제 반환 | work_order, consumption, material_return, raw_lot. IN_PROGRESS·원소비별 최신 순량·실제 반환 검증 | 작업지시 → 원소비 배타 잠금 → rawlot 갱신. 최신 반환합+신규 ≤ 원소비 | 반환 이력·해당 LOT 가용량 복원 원자 처리. 복원상한·상태 실패 시 모두 롤백 |
| T6 생산 완료 | work_order, LOT, result. IN_PROGRESS·실적 존재·누적 >0·누적=지시량 | 작업지시 배타 잠금 → 최신 실적 합계. 검사 양품합을 같은 시점에 계산 | 상태 COMPLETED·completed 필드·target 저장. 미달·무실적이면 전부 미반영. 검사 완료 요구 없음 |
| T7 최종 검사 | receipt, work_order, LOT, quality_inspection. COMPLETED·대상/회차수량 합계·누적상한 검증 | 요청 키 → 작업지시 배타 잠금 → 최신 검사합. 새 회차번호는 잠금 안에서 MAX(round_no)+1 배정 추천. 회차 UK·receipt UK 병행 | 회차·수량·요청 결과 원자 저장. 실패 시 신규 검사·요청 롤백. 작업지시 상태 변경 없음 |

T5의 반환량 증가는 같은 rawlot의 등록수량 이하인지 조건부 변경과 CHECK로 보호한다. 원소비합 상한이 이미 유효하더라도 보조 방어로 사용하며 실제 반환 의미를 CHECK로 검증할 수는 없다.

### 9.3 경합과 격리수준

READ COMMITTED를 추천하는 이유는 부모 잠금 뒤 자식 합계 일반 조회가 각 조회 시점의 최신 커밋을 보도록 하기 위해서다. 부모 잠금이 풀릴 때까지 다른 동일 부모 writer를 차단하는 규약을 모든 경로가 지켜야 한다. 격리수준만 낮추거나 높인다고 누적량 상한이 보장되지 않는다. [MySQL 일관 읽기·격리수준](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html)

REPEATABLE READ 대안은 기존 읽기 스냅샷 때문에 부모 잠금 뒤 일반 합계도 오래된 상태일 수 있다. 유지한다면 자식의 현재 읽기·전체 writer 직렬화·또는 원자적 누적값 관리 전략을 실제 SQL과 함께 검증해야 한다. 부모 잠금 한 줄만 넣고 이전 스냅샷의 SUM을 신뢰하지 않는다. 잠금 읽기는 명시적 트랜잭션 안에서 사용하고 커밋·롤백 시 해제되는 전제로 설계한다. [MySQL 잠금 읽기](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)

| 동시 요청 | 보장 방식·결과 |
|---|---|
| 같은 rawlot 잔량 40에서 30씩 소비 | 서로 다른 작업지시라도 rawlot 조건부 변경으로 1개만 성공. 잔량 10, 성공 소비 30 |
| 원소비 60에서 40씩 반환 | 원소비별 직렬화 후 두 번째는 잔여 20이므로 실패 |
| 같은 작업지시 첫 실적 60·40 | 작업지시 직렬화, LOT 1개, 두 개 정상 키는 각 검증 후 총 100 |
| 첫 실적 60·60 | 두 번째는 누적 120이므로 거부. 서로 다른 키가 업무수량 검증을 면제하지 않음 |
| 실적과 완료 | 완료가 먼저 잠그면 당시 최신 합계로 판단. 미달이면 거부. 등록이 먼저면 반영된 합계로 완료 판단. 완료 뒤 등록·정정 금지 |
| 검사 잔여 35에서 서로 다른 회차 30씩 | 첫 회차 후 5, 두 번째는 초과라 거부. 같은 요청 재전송은 새 회차로 배정하지 않음 |

데드락·대기 timeout·제약 실패는 업무 트랜잭션 전체를 롤백한다. 부분 변경만 재시도하거나 catch 후 성공 반환하지 않는다. 재시도는 새 트랜잭션에서 기존 키를 유지하며 상태·수량을 재검증한다. 허용 재시도 횟수·timeout·오류 응답은 후속 구현 설정이다. AUTO_INCREMENT 번호의 공백은 실패 복구 문제로 취급하지 않는다.

## 10. 동시성 및 중복 요청 처리

### 10.1 요청 식별 계약 추천

기본 키는 `(scope_key,operation_code,request_key)`다. scope_key는 서버 인증 문맥의 안정적인 주체 식별자를 사용하는 안을 추천한다. 세션이 바뀌었다고 scope가 바뀌는 임시 세션 ID는 사용하지 않는다. 실제 로그인·권한 모델과 연계한 범위는 구현 전 승인해야 한다. operation_code는 서버의 업무 종류이며 클라이언트 입력으로 바꾸지 않는다.

request_key는 길이 1~64의 ASCII 영문·숫자·하이픈·밑줄로 제한하는 기술 계약을 추천하며 공백·제어문자는 거부한다. ascii_bin만 지정하고 후행 공백까지 서로 다른 키로 처리된다고 가정하지 않는다. 같은 업무의 재전송은 같은 키·같은 입력을 보낸다. 다른 정상 소비·실적·검사 회차는 다른 키를 사용한다. 같은 수량·시각이라고 시간창으로 합치지 않는다. 인증·권한 확인은 기존 결과 재응답에도 수행하여 다른 주체의 결과가 노출되지 않게 한다.

fingerprint는 저장 대상 ID, 작업지시/LOT 식별자, 수량, 업무시각, 작업자 등 실제 의미가 있는 요청 필드를 버전이 정해진 규칙으로 정규화한 후 계산한다. 수량 1과 1.000000, 시간대 표현, 필드 순서 차이는 같은 정규화 값이 되게 한다. 서버가 배정하는 receipt_id·LOT ID·검사 회차 번호는 요청 fingerprint 입력에 섞지 않는다. 입력 검증 후 정규화하며 해시값만 믿고 허용되지 않는 수량을 통과시키지 않는다.

### 10.2 결과 연결·원자성

1. 권한·요청 형식을 검증하고 요청 키를 선점한다. Receipt 선점과 업무 변경을 별도 커밋하지 않는다.
2. 새 요청이면 9절의 잠금·업무 검증·원천 저장을 수행한다.
3. 업무 결과는 각 테이블의 receipt_id FK로 연결한다. 성공 요약에 결과 식별자·최초 수량·revision/회차를 저장하고 processed_at을 설정한다.
4. 해당 operation에 정확히 1개 결과가 연결되었는지 확인한 뒤 커밋한다. JSON 결과 ID만 저장하고 실제 관계를 생략하지 않는다.
5. 실패하면 Receipt와 업무 변경을 함께 롤백한다. 결과 없는 선점 행을 정상 커밋하지 않는다.

같은 키 동시 도착은 DB UK가 한 업무만 통과시킨다. 중복 키 예외를 단순 무시하지 않고 실패한 시도 트랜잭션을 종료한 후 새 읽기 범위에서 기존 성공 Receipt를 확인한다. 먼저 요청한 트랜잭션이 롤백했다면 성공 기록이 없으므로 같은 키로 새 업무 시도가 가능하다. 응답 timeout·commit 여부 불명확 시도도 키를 바꾸지 않고 재조회한다.

| 입력 | 추천 처리 |
|---|---|
| 같은 키·같은 fingerprint | 기존 성공 결과 재조회, 업무 변경 0회 추가 |
| 같은 키·다른 fingerprint | 키 재사용 오류로 거부, 기존 성공 결과 덮어쓰기 금지 |
| 다른 키 | 별도 정상 요청으로 상태·누적량·가용량 재검증 |

실적이 이후 정정되더라도 재전송은 최초 결과 요약을 반환하고 최초 수량을 현재 실적에 다시 쓰지 않는다. 현재 revision은 별도 조회 정보다. Receipt 보존기간을 지나 삭제하면 같은 키 재전송의 중복 방지가 사라질 수 있으므로, 운영 중 만료·삭제를 임의 도입하지 않는다. 구체적인 응답 status·본문·보존기간·동일 키 동시 대기 정책은 후속 기술 설계다.

### 10.3 검사 요청의 확장과 보장 수준

논리 ERD의 Receipt 연결은 소비·실적 두 종류였다. 이번 요청에서 검사 재전송 방지도 요구했으므로 quality_inspection.receipt_id와 QUALITY_INSPECTION operation을 추가하는 물리 추천안을 명시했다. 기존 ERD 파일을 수정한 것은 아니며 물리 설계 채택 후 논리 문서 정합성 갱신이 필요하다.

회차번호는 부모 잠금 안에서 배정하며 같은 요청의 재전송에서 재배정하지 않는다. 서로 다른 정상 키의 동일 수량 검사는 잔여량 범위에서 별개 회차가 될 수 있다. 동일 실물을 반복 검사했는지는 시스템 보장 범위가 아니다. 이 방향은 이번 요청의 명시 조건이며 기존 Business Rules/ERD에는 아직 해당 보장 범위 결정이 미확정으로 남아 있다. 따라서 정책 문서의 동기화 필요를 보고하되 임의 수정하지 않는다.

### 10.4 반환·정정 요청의 남은 재시도 문제

일반 실적 정정은 expected revision으로 오래된 요청의 반복 변경을 차단한다. 실제 반환의 순소비 상한은 중복 요청 자체를 식별하지는 못한다. 실제 20개 반환 요청이 두 번 입력되고 순소비가 충분하면 두 번 모두 상한 이내일 수 있다. 반환까지 Receipt를 확대하는 방식을 추천하지만 이번 기본 명세에 새 operation·FK를 자동 포함하지 않았다. **반환 요청 재전송 계약은 구현 전 기술 설계 결정 필요**이며, 물리 반환과 기록 오류 보정을 섞어서 해결하지 않는다.

## 11. 주요 조회 구조 및 집계 중복 방지

### 11.1 권장 접근

| 방법 | 장점 | 한계·사용처 |
|---|---|---|
| 원천별 사전 집계 후 조인 | 수량 행 곱 방지, 한 조회 시점으로 요약 가능 | 기본 추천. 집계 범위를 LOT·작업지시로 제한 |
| 상관 서브쿼리 | 작은 상세 화면에서 이해하기 쉬움 | 목록 N건마다 반복 비용이 생길 수 있어 실행계획 확인 필요 |
| 도메인별 개별 조회 | 내역과 요약 책임 명확, 복잡한 조인 감소 | 요청 수 증가·여러 시점 데이터 혼합. 일관성이 필요하면 같은 조회 트랜잭션 전략 필요 |

예를 들어 실적 2행·소비 3행·반환 2행·검사 2행을 모두 직접 JOIN하고 SUM하면 수량이 행 수만큼 중복된다. DISTINCT SUM으로 고치면 같은 값인 서로 다른 정상 실적이 사라질 수 있어 올바른 해결이 아니다.

추천 집계 순서:

1. result를 finished_lot_id별 생산·양품·불량 합으로 집계한다.
2. return을 consumption_id별 반환합으로 집계한다.
3. consumption에 2의 반환합을 붙여 순소비를 계산하고 work_order_id·raw_lot_id 또는 item·unit별로 묶는다.
4. inspection을 finished_lot_id별 검사·합격·불합격 합으로 집계한다.
5. 위 요약끼리 work_order·finished_lot 관계로 결합한다. 0건 집계에서 합계 0을 표현하는 것은 '이력 없음'의 수학적 의미이며 필수 FK·요청 오류를 NULL 기본값으로 우회하는 것과 다르다.

한 품목의 BOM 기대량과 순소비를 비교할 때 전체 지시량 기준임을 표시한다. 제품·원자재·단위가 다른 수량을 전체 한 숫자로 합산하지 않는다. Defect 상세·ResultCorrection·Receipt JSON은 수량 집계 원천이 아니다.

### 11.2 LOT 역추적 경로

finished_goods_lot → work_order → 적용 bom_version → bom_component, routing_revision → routing_step을 조회한다. 실제 소비는 work_order → material_consumption → raw_material_lot → item으로 찾고 material_return을 원소비별 집계하여 순소비를 표시한다. 생산 결과는 LOT → result·correction, 품질은 LOT → inspection 경로로 찾는다.

현재 최신 BOM 구성으로 과거 원자재 사용량을 재계산하지 않는다. BOM은 당시 예상량·구성 기준이고 실제 사용 이력은 소비·반환 원천이다. 작업지시 단위 사용량을 개별 실적 또는 개별 제품에 배분하지 않는다. 단위 변환·시리얼 추적이 없는 한 그 이상의 추적 정밀도를 주장하지 않는다.

## 12. 대표 시나리오 정적 검토

아래는 모델로 설명되는 결과이며 실제 DB 테스트를 실행한 결과가 아니다.

| 번호 | 시나리오 | DB 보호 | Service·트랜잭션 검증과 기대 결과 |
|---|---|---|---|
| 1 | 지시 100, 실적 60+40 후 완료 | 실적 CHECK, LOT UK·FK | T1/T2 누적상한, T6 합계=100·실적존재·0금지. 완료·양품 검사대상 동시 확정 |
| 2 | 같은 작업지시 최초 실적 동시 등록 | LOT.workorder UK, 요청 UK | 작업지시 직렬화, 재조회 후 기존 LOT 사용. 서로 다른 정상 키면 상한 내 각각 처리 |
| 3 | 가용 40에서 소비 30씩 동시 요청 | rawlot CHECK, 소비 FK·요청 UK | 조건부 차감 영향행수 검증. 소비 1건 성공, 가용 10. 실패 소비 이력 없음 |
| 4 | 소비 60, 반환 20 후 추가 50 | 반환 FK·양수 CHECK | 원소비 잠금·최신 반환합. 남은 40 초과라 50 거부. 실제 첫 반환만 원장·가용량 반영 |
| 5 | 실적 전후 정정 이력 | correction FK·(result,to_revision) UK·전후 CHECK | IN_PROGRESS·expected revision·현재 before 일치·상한. 현재값과 이력 원자 저장, 원자재 변경 없음 |
| 6 | COMPLETED에서 실적 정정 | 행내 CHECK만으로 상태 전이까지 해결 불가 | 작업지시 잠금 후 상태 검증으로 거부. 실적·이력·원자재 변경 없음 |
| 7 | 양품 95, 검사 60/58/2와 35/34/1 | 검사 회차·요청 UK, 검사 CHECK | COMPLETED, 대상 95. 누적 합격 92·불합격 3·미검사 0, 생산불량 5와 구분 |
| 8 | 검사 60 후 추가 40 요청, 대상 95 | 행내 검사 CHECK만으로 총량 보장 불가 | 작업지시 잠금·최신 검사합 검증. 잔여35 초과로 거부 |
| 9 | 동일 요청 ID 재전송 | Receipt 키 UK와 결과 receipt UK | 같은 내용 성공 재조회·추가 변경 없음. 다른 내용 거부. 각 정상 키는 별도 검증 |
| 10 | 완제품 LOT의 실제 원자재 역추적 | LOT·WO·consumption·rawlot·return FK | 원소비별 반환 선집계로 실제 순소비 조회. 임의 실적별 배분 없음 |
| 11 | 과거 생산 이후 BOM 새 버전 생성 | (bom,version_no) UK, WO의 기존 버전 FK, 삭제 RESTRICT | 기존 버전 first_used_at 검증으로 수정 차단. 새 version/component는 새 PK. 과거 WO FK 유지 |
| 12 | 작업지시 완료와 실적 등록 동시 요청 | 상태 행내 CHECK·실적 CHECK·LOT UK | 같은 WO 잠금으로 순서 확정. 완료 먼저면 당시 합계 판정, 등록 먼저면 반영 합계 판정. 완료 뒤 추가 등록 금지 |

추가 대조: BR-01은 LOT UK/T1, BR-02는 소비·반환 원장/T4·T5, BR-03은 상태/T6, BR-04는 11.2절, BR-05는 T7과 수량식에 대응한다. BD-01~10의 완료·편차·정정·반환·사후정정금지·수량구분·요청고유ID·원자성·무실적완료금지·검사후정정금지는 유지했다. 취소와 요약 상태 등 기존 미확정 정책은 확정하지 않았다.

## 13. 설계 선택 근거 및 소비 입력 오류 대안

### 13.1 실제 반환과 입력 오류는 다른 사건

| 사례 | 기본 처리·이력 | 추가 구조·트랜잭션 비용 | 결정 상태 |
|---|---|---|---|
| A. 원소비 등록 후 실제 미사용 자재 반환 | material_return이 원소비 참조, 원장·가용량 동시 복원 | 기본 2테이블 변경과 WO·원소비·rawlot 잠금 | 승인 규칙에 따른 기본안 |
| B. 소비량 입력 오류 | 실제 반환이 없는 경우 material_return으로 기록 금지. 불변 correction 기록으로 원래 소비와 before/after 또는 delta 연결 | material_consumption_correction 1개 추가 후보, 현재 유효 소비 해석·가용량 보정·이력 원자 처리 필요 | 설계 결정 필요, 기본 18개에 미포함 |
| C. 잘못된 LOT 선택 | 원소비를 삭제하지 않고 오류 원본·올바른 LOT·교정 사유를 연결하는 교정 사건 후보 | 별도 correction 헤더+대상 연결 1~2개 후보, 두 rawlot 잠금·잔량 검증·교정 원장 반영 필요 | 복잡한 LOT 교체를 MVP에 자동 포함하지 않음 |

B 대안 1은 현재 유효 소비값과 correction 전후 이력을 분리하는 방식이다. 조회는 쉽지만 MaterialConsumption을 불변 원본으로 정의한 현재 모델과 충돌하므로 별도 원본·현재 투영 구조가 필요하다. 대안 2는 원소비를 그대로 두고 signed delta의 기록 교정 원장을 추가하는 방식이다. 원본 보존에 적합하지만 실제 반환합과 교정합을 분리하고 순소비·가용량 산식에 교정량을 포함해야 한다. **B를 채택한다면 대안 2를 우선 검토**하되 감소량 상한·증가 시 가용량 검증·실제 소비 사실 확인·승인 주체는 업무 결정이 필요하다.

C도 실제 반환 후 재소비가 일어난 경우만 A+새 소비로 표현한다. 단순 잘못된 클릭을 물리 반환·재투입으로 꾸미지 않는다. 기록 교정이면 원본을 가리키는 별도 교정 이벤트와 교체 대상 관계가 필요하고 양쪽 품목·단위·BOM·작업지시 상태·수량을 검증해야 한다. 기본 모델은 A와 실제 추가 소비만 지원하며 B/C의 기능 동작을 임의 확정하지 않는다.

현재 기본 가용량 산식에는 B/C 교정 원장이 없다. 추후 교정을 채택하면 영향을 받는 원천·집계·트랜잭션을 함께 변경해야 한다. 기존 논리 ERD 9.2절의 '감소 정정을 반환으로 표현' 추천은 **실제 반환이 있는 경우에만 적용 가능**하며 기록 오류 전반에 적용하면 이번 요청과 충돌한다. 기존 문서는 수정하지 않고 이 제한을 보고한다.

### 13.2 주요 기술 선택

| 선택 | 이유 | 대안·한계 |
|---|---|---|
| 공유 불변 버전 + 적용 FK | 과거 기준과 재사용을 함께 보존 | WO별 스냅샷은 격리는 좋으나 복제·책임 증가 |
| Result는 LOT FK만 보유 | WO와 LOT의 이중 귀속 불일치 방지 | 성능 때문에 WO FK 추가 시 복합 일치 제약 필요 |
| 소비·물리 반환 분리 | 원소비와 실제 반환 사실을 명확히 보존 | 범용 signed 거래는 사건별 검증·조회 복잡도 증가 |
| Result 현재값 + Correction | 합계 원천 단일화, 전후 이력 보존 | 모든 revision 저장 모델은 현재 버전 선택 누락 위험 |
| LOT 수량 기반 검사 | 승인된 MVP 범위, 여러 회차·수량 상한 구현 가능 | 실물 중복 검사·시리얼 보장 없음 |
| Receipt + DB UK | 재전송과 다른 정상 업무를 명확히 구분 | 단순 시간 비교는 정상 반복과 재전송을 구분 못 함 |
| 행 잠금 + 조건부 수량 변경 | 작업지시 상태·합계와 rawlot 경쟁을 각 불변식에 맞게 처리 | 전체에 일괄 낙관적 잠금만 적용하면 자식 합계 누락 가능 |
| 수량 사전 집계 | 다중 JOIN에 의한 수량 곱 방지 | 모든 합계를 저장하면 정정 경로의 동기화 책임 증가 |

## 14. 미확정 사항과 구현 전 결정 항목

### 14.1 남은 업무 결정

| 항목 | 필요한 결정 |
|---|---|
| LOT 번호·업무 코드 유일성 | 전역/품목별 범위·채번·ASCII 코드 허용. 최종 UK 작성 전 필요 |
| 수량·단위 | EA 정수·원자재 소수 정밀도·변환·반올림·개별 0기록 허용 |
| 소비 기록 오류 B/C | 허용 범위·실제 사실 확인·승인 주체·교정 원장 도입 여부. 물리 반환과 구분 |
| 생산실적 취소 | 삭제·음수 상쇄·LOT 자동 삭제를 가정하지 않음 |
| 부분 합격 LOT | 요약 품질 상태와 사용 가능량·검사대상0 표시. 수량 분리는 이미 확정 |
| 원자재 예약·등록량 정정 | 현재 기본 산식 밖의 정책이므로 자동 포함하지 않음 |
| 불량 상세 2개 후보 | 원인별 합계·중복·정정 상세 이력 정책 승인 필요 |
| 계획 변경·취소·초과 배분 / 지시 변경 | 기존 이력과 상태를 훼손하지 않는 범위 결정 |
| 기준정보 수정·삭제 | 품목은 코드·구분·단위 고정, 명칭·사용 여부 수정, 물리 삭제 금지로 확정. 사용된 버전 불변과 완료 LOT 삭제 등 다른 도메인 정책은 별도 항목 유지 |
| 주체·권한 | 작업자 식별·안정적인 scope_key·시스템 접근 권한과 공개 데모 변경 범위 |

### 14.2 기술 설계·검증 필요

1. 추천 MySQL 8.4.x와 SQL mode·문자셋·collation·시간대·Spring 트랜잭션 격리를 실제 환경에서 확인한다. 본 작업에서는 실행하지 않았다.
2. 조건부 업무 코드·LOT UK를 확정하고, 모든 FK의 이름·타입·지원 인덱스와 상태 CHECK를 DDL로 옮길 준비를 한다. 미확정 상태로 실제 테이블을 먼저 만들지 않는다.
3. 사용된 버전의 모든 편집 경로가 부모 잠금을 거치도록 설계한다. first_used_at만으로 불변을 보장한다고 가정하지 않는다.
4. request_receipt의 정확한 scope·정규화 버전·해시·최초 결과 JSON 스키마·응답·보존기간을 확정한다. 반환의 재전송 방지 확대 여부도 구현 전 결정한다.
5. READ COMMITTED + 부모 잠금에서 최신 합계와 조건부 UPDATE의 영향행수, rollback·데드락 재시도·commit 불확실 재조회 전략을 실제 SQL로 검증할 계획을 세운다.
6. 기본 테이블 18개와 조건부 2개 후보를 구분하여 구현 범위를 승인받는다. 세부 코드·단위 정책·불량 상세가 확정되지 않은 영역은 구현하지 않는다.

### 14.3 논리 ERD·업무 문서와의 정합성 보고

- 작업지시-LOT 최대1개, 실적의 단일 LOT 경로, 소비-반환 FK, 적용 버전 참조, 실적 정정 이력은 기존 논리 관계를 유지했다.
- 검사 Receipt 연결과 물리 보장 범위는 이번 요청을 근거로 한 명시적 추천 확장이다. 기존 ERD와 Business Rules에는 검사 실물 중복 보장 수준이 미결정으로 남아 있으므로 후속 승인된 문서 동기화가 필요하다. 기존 파일은 수정하지 않았다.
- 소비 감소 정정을 물리 반환으로 일반화하던 논리 문서 추천은 실제 반환과 기록 오류를 구별해야 한다. 본 설계는 A만 기본 물리 반환으로 구현하도록 제한하고 B/C는 대안·업무 결정으로 남겼다.
- 명칭 스냅샷·단위 보존·first_used_at은 기존 버전 보존 책임을 구체화한 기술 속성이다. 새로운 생산 업무 상태나 승인되지 않은 출하·창고 기능은 추가하지 않았다.
- 12개 대표 시나리오는 원천·제약·Service 검증으로 정적으로 설명 가능하다. 실제 DDL 문법·CHECK 강제·FK 생성·동시 실행·성능은 검증하지 않았으며 테스트 통과를 주장하지 않는다.

다음 작업은 미확정 유일성·단위·소비 오류 정정 범위와 문서 동기화 항목을 검토한 뒤 승인된 범위의 DDL 초안을 작성하는 것이다. 본 작업은 이 문서 작성과 정적 검토에서 종료한다. 기존 문서 변경·Java 구현·DB 생성·DDL 실행·빌드·테스트·린트·서버·브라우저·Commit·Push·Rebase·Reset은 수행하지 않았다.
