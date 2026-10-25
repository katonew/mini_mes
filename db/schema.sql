-- Mini MES MVP DDL 초안: MySQL 8.4 / InnoDB.
-- Spring Boot 디렉터리가 없는 현재 저장소에서는 db/schema.sql 한 파일만 사용한다.
-- 빈 대상 스키마에 적용할 생성문이다. DB 생성·DROP·IF NOT EXISTS·자동 마이그레이션은 포함하지 않는다.
-- 실제 MySQL 실행 검증은 수행하지 않았다. 연결 문자셋 utf8mb4, 세션 시간대 UTC,
-- strict SQL mode 확인은 배포 전 필요하다. DATETIME 자체에는 시간대가 저장되지 않는다.
--
-- 기존 설계와 달리 이번에 승인된 기준:
--   업무 품목/공정/BOM/Routing 코드 각각 전역 UNIQUE.
--   원자재·완제품 LOT 번호는 각각 해당 LOT 종류 전체 품목 범위에서 UNIQUE.
--   반환·검사도 receipt_id UNIQUE FK로 같은 요청 재전송의 중복 반영을 보호한다.
--   제품·계획·지시·실적·검사 및 실적 정정 전후 값은 EA 정수 CHECK를 적용한다.
--   원자재 등록·소비·반환·BOM 구성 소요량은 DECIMAL(18,6)으로 소수 단위 확장을 허용한다.
--
-- DECIMAL(18,6)은 정수부 12자리·소수부 6자리다. CHECK는 타입 변환 후의 값을 본다.
-- Service는 원본 입력에서 범위·정밀도·EA 정수 여부를 먼저 검증해야 한다.
-- 1.0000001 같은 입력을 DB 반올림 후 1로 받아들이거나 잘못된 값을 절삭해서는 안 된다.
-- 단위 변환·서로 다른 품목/단위 수량의 합산은 하지 않는다. 원자재 EA의 정수성은
-- 해당 품목·LOT·BOM 단위를 확인하는 Service 책임이며 다른 테이블 참조 CHECK로 보장하지 않는다.
-- 개별 0생산/0검사/0계획/0지시 등록의 허용 정책은 미확정이므로 최소 비음수 제약만 둔다.
-- 0수량 완료·실적 없는 완료는 항상 Service에서 금지한다.
--
-- Service/트랜잭션 책임(이 DDL만으로 보장되지 않음):
--   허용 상태 전이, 누적 생산 <= 지시량, 완료 시 실적 존재·누적 생산 > 0·지시량 일치.
--   최초 실적·LOT·Receipt 원자 생성 및 적용 BOM/Routing 버전 사용 후 불변성.
--   IN_PROGRESS에서만 일반 실적 등록/정정·소비·반환, COMPLETED에서만 최종 검사.
--   소비의 적용 BOM·품목·단위 일치, 조건부 가용수량 차감, 순소비 이내 반환과 원자 복원.
--   완료 양품으로 검사대상 확정, 최신 누적 검사수량 상한, 업무별 배타 행 잠금.
--   READ COMMITTED + 부모 행 잠금 추천. 격리수준 설정만으로 동시성이 해결되지 않는다.
--   같은 요청 키·같은 payload는 기존 성공 재조회, 다른 payload는 거부.
--   결과 없는 Receipt 선점만 영구 커밋 금지, 업무 원본·성공 요약은 동일 트랜잭션 저장.
--   Receipt의 operation과 실제 결과 종류 일치·정확히 한 종류 결과 존재는 Service에서 검증.
--   payload_fingerprint의 정규화, 공백 없는 요청 키·안정적인 scope는 Service에서 검증.
--
-- MVP 제외: 예약·창고·출고·시리얼·재검사·재작업·생산실적 취소·완료 후 사후 정정,
-- 복잡한 원자재 LOT 교체 및 소비 기록 오류 정정. material_return은 실제 반환만 기록한다.
-- FR-10 불량 내역 요구는 유지한다. production_defect/inspection_defect는 이번 18개에서 제외하며,
-- 원인별 수량·복수 사유 중복·정정 이력 정책을 확정한 뒤 후속 설계한다.
-- 실적 정정 이력·Receipt JSON·불량 상세는 생산수량의 집계 원천이 아니다.

-- 01. 품목
CREATE TABLE item (
    item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    item_kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    unit_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    is_active TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (item_id),
    UNIQUE KEY uq_item_code (item_code),
    CONSTRAINT ck_item_active CHECK (is_active IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 02. BOM 헤더
CREATE TABLE bom (
    bom_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_item_id BIGINT UNSIGNED NOT NULL,
    bom_code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (bom_id),
    UNIQUE KEY uq_bom_code (bom_code),
    KEY ix_bom_product (product_item_id),
    CONSTRAINT fk_bom_product FOREIGN KEY (product_item_id)
        REFERENCES item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 03. BOM 버전: first_used_at 이후 내용·구성 변경 금지는 Service 잠금 규약으로 보호한다.
CREATE TABLE bom_version (
    bom_version_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bom_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    base_qty DECIMAL(18,6) NOT NULL,
    product_unit_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    first_used_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (bom_version_id),
    UNIQUE KEY uq_bom_version (bom_id, version_no),
    CONSTRAINT fk_bom_version_bom FOREIGN KEY (bom_id)
        REFERENCES bom (bom_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_bom_version_no CHECK (version_no > 0),
    CONSTRAINT ck_bom_version_base_ea CHECK (
        base_qty > 0 AND base_qty = FLOOR(base_qty)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 04. BOM 구성: 같은 품목의 복수 구성행 금지는 승인되지 않아 품목별 UK를 추가하지 않는다.
CREATE TABLE bom_component (
    bom_component_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bom_version_id BIGINT UNSIGNED NOT NULL,
    material_item_id BIGINT UNSIGNED NOT NULL,
    line_no INT UNSIGNED NOT NULL,
    required_qty DECIMAL(18,6) NOT NULL,
    material_unit_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (bom_component_id),
    UNIQUE KEY uq_bom_component_line (bom_version_id, line_no),
    KEY ix_bom_component_item (material_item_id),
    CONSTRAINT fk_bom_component_version FOREIGN KEY (bom_version_id)
        REFERENCES bom_version (bom_version_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_bom_component_item FOREIGN KEY (material_item_id)
        REFERENCES item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_bom_component_line CHECK (line_no > 0),
    CONSTRAINT ck_bom_component_qty CHECK (required_qty > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 05. 공정 정의
CREATE TABLE manufacturing_process (
    process_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    process_code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    description VARCHAR(500) NULL DEFAULT NULL,
    is_active TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (process_id),
    UNIQUE KEY uq_process_code (process_code),
    CONSTRAINT ck_process_active CHECK (is_active IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 06. Routing 헤더
CREATE TABLE routing (
    routing_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_item_id BIGINT UNSIGNED NOT NULL,
    routing_code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (routing_id),
    UNIQUE KEY uq_routing_code (routing_code),
    KEY ix_routing_product (product_item_id),
    CONSTRAINT fk_routing_product FOREIGN KEY (product_item_id)
        REFERENCES item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 07. Routing Revision
CREATE TABLE routing_revision (
    routing_revision_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    routing_id BIGINT UNSIGNED NOT NULL,
    revision_no INT UNSIGNED NOT NULL,
    first_used_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (routing_revision_id),
    UNIQUE KEY uq_routing_revision (routing_id, revision_no),
    CONSTRAINT fk_routing_revision_routing FOREIGN KEY (routing_id)
        REFERENCES routing (routing_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_routing_revision_no CHECK (revision_no > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 08. Routing 공정 순서
CREATE TABLE routing_step (
    routing_step_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    routing_revision_id BIGINT UNSIGNED NOT NULL,
    process_id BIGINT UNSIGNED NOT NULL,
    step_no INT UNSIGNED NOT NULL,
    process_name_snapshot VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (routing_step_id),
    UNIQUE KEY uq_routing_step (routing_revision_id, step_no),
    KEY ix_routing_step_process (process_id),
    CONSTRAINT fk_routing_step_revision FOREIGN KEY (routing_revision_id)
        REFERENCES routing_revision (routing_revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_routing_step_process FOREIGN KEY (process_id)
        REFERENCES manufacturing_process (process_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_routing_step_no CHECK (step_no > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 09. 생산계획: 작업지시 배분·계획 변경/취소 정책은 이 DDL에서 새로 정하지 않는다.
CREATE TABLE production_plan (
    production_plan_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_item_id BIGINT UNSIGNED NOT NULL,
    planned_qty DECIMAL(18,6) NOT NULL,
    planned_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (production_plan_id),
    KEY ix_plan_product (product_item_id),
    CONSTRAINT fk_plan_product FOREIGN KEY (product_item_id)
        REFERENCES item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_plan_qty_ea CHECK (
        planned_qty >= 0 AND planned_qty = FLOOR(planned_qty)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 10. 작업지시: 아래 CHECK는 현재 행 상태만 보호하며 상태 전이·자식 합계는 검증하지 않는다.
CREATE TABLE work_order (
    work_order_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    production_plan_id BIGINT UNSIGNED NOT NULL,
    product_item_id BIGINT UNSIGNED NOT NULL,
    bom_version_id BIGINT UNSIGNED NOT NULL,
    routing_revision_id BIGINT UNSIGNED NOT NULL,
    ordered_qty DECIMAL(18,6) NOT NULL,
    product_unit_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'READY',
    inspection_target_qty DECIMAL(18,6) NULL DEFAULT NULL,
    started_at DATETIME(6) NULL DEFAULT NULL,
    started_by VARCHAR(64) NULL DEFAULT NULL,
    completed_at DATETIME(6) NULL DEFAULT NULL,
    completed_by VARCHAR(64) NULL DEFAULT NULL,
    cancelled_at DATETIME(6) NULL DEFAULT NULL,
    cancelled_by VARCHAR(64) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (work_order_id),
    KEY ix_wo_plan (production_plan_id),
    KEY ix_wo_product (product_item_id),
    KEY ix_wo_bom (bom_version_id),
    KEY ix_wo_routing (routing_revision_id),
    CONSTRAINT fk_wo_plan FOREIGN KEY (production_plan_id)
        REFERENCES production_plan (production_plan_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_wo_product FOREIGN KEY (product_item_id)
        REFERENCES item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_wo_bom_version FOREIGN KEY (bom_version_id)
        REFERENCES bom_version (bom_version_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_wo_routing_revision FOREIGN KEY (routing_revision_id)
        REFERENCES routing_revision (routing_revision_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_wo_qty_ea CHECK (
        ordered_qty >= 0 AND ordered_qty = FLOOR(ordered_qty)
    ),
    CONSTRAINT ck_wo_status CHECK (
        status IN ('READY', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT ck_wo_target_ea CHECK (
        inspection_target_qty IS NULL
        OR (
            inspection_target_qty >= 0
            AND inspection_target_qty <= ordered_qty
            AND inspection_target_qty = FLOOR(inspection_target_qty)
        )
    ),
    CONSTRAINT ck_wo_state_fields CHECK (
        (
            status = 'READY'
            AND started_at IS NULL AND started_by IS NULL
            AND completed_at IS NULL AND completed_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL
            AND inspection_target_qty IS NULL
        )
        OR (
            status = 'IN_PROGRESS'
            AND started_at IS NOT NULL AND started_by IS NOT NULL
            AND completed_at IS NULL AND completed_by IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL
            AND inspection_target_qty IS NULL
        )
        OR (
            status = 'COMPLETED' AND ordered_qty > 0
            AND started_at IS NOT NULL AND started_by IS NOT NULL
            AND completed_at IS NOT NULL AND completed_by IS NOT NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL
            AND inspection_target_qty IS NOT NULL
        )
        OR (
            status = 'CANCELLED'
            AND started_at IS NULL AND started_by IS NULL
            AND completed_at IS NULL AND completed_by IS NULL
            AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
            AND inspection_target_qty IS NULL
        )
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 11. 완제품 LOT: 최초 실적과 같은 트랜잭션에서만 생성한다.
CREATE TABLE finished_goods_lot (
    finished_lot_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT UNSIGNED NOT NULL,
    lot_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (finished_lot_id),
    UNIQUE KEY uq_fg_work_order (work_order_id),
    UNIQUE KEY uq_fg_lot_no (lot_no),
    CONSTRAINT fk_fg_work_order FOREIGN KEY (work_order_id)
        REFERENCES work_order (work_order_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 12. 요청 기록: 자식의 FK가 참조할 수 있도록 네 업무 결과 테이블보다 먼저 생성한다.
-- 아래 성공 필드 CHECK는 존재하는 initial_outcome_snapshot 컬럼을 사용한다.
-- 두 필드가 NULL인 선점 행은 같은 트랜잭션 내부에서만 허용하며 미완료 커밋은 Service가 금지한다.
CREATE TABLE request_receipt (
    receipt_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scope_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_fingerprint BINARY(32) NOT NULL,
    initial_outcome_snapshot JSON NULL DEFAULT NULL,
    processed_at DATETIME(6) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (receipt_id),
    UNIQUE KEY uq_request_key (scope_key, operation_code, request_key),
    CONSTRAINT ck_receipt_operation CHECK (
        operation_code IN (
            'MATERIAL_CONSUME', 'MATERIAL_RETURN',
            'PRODUCTION_RESULT', 'QUALITY_INSPECTION'
        )
    ),
    CONSTRAINT ck_receipt_keys_present CHECK (
        CHAR_LENGTH(TRIM(scope_key)) > 0
        AND CHAR_LENGTH(TRIM(request_key)) > 0
    ),
    CONSTRAINT ck_receipt_success_fields CHECK (
        (processed_at IS NULL AND initial_outcome_snapshot IS NULL)
        OR (processed_at IS NOT NULL AND initial_outcome_snapshot IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 13. 현재 유효 생산실적: 작업지시는 finished_goods_lot 경로로만 참조한다.
CREATE TABLE production_result (
    production_result_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    finished_lot_id BIGINT UNSIGNED NOT NULL,
    receipt_id BIGINT UNSIGNED NOT NULL,
    produced_qty DECIMAL(18,6) NOT NULL,
    good_qty DECIMAL(18,6) NOT NULL,
    defect_qty DECIMAL(18,6) NOT NULL,
    produced_at DATETIME(6) NOT NULL,
    worker_ref VARCHAR(64) NOT NULL,
    revision_no INT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (production_result_id),
    UNIQUE KEY uq_result_receipt (receipt_id),
    KEY ix_result_lot (finished_lot_id),
    CONSTRAINT fk_result_lot FOREIGN KEY (finished_lot_id)
        REFERENCES finished_goods_lot (finished_lot_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_result_receipt FOREIGN KEY (receipt_id)
        REFERENCES request_receipt (receipt_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_result_quantities CHECK (
        produced_qty >= 0 AND good_qty >= 0 AND defect_qty >= 0
        AND produced_qty = good_qty + defect_qty
    ),
    CONSTRAINT ck_result_qty_ea CHECK (
        produced_qty = FLOOR(produced_qty)
        AND good_qty = FLOOR(good_qty)
        AND defect_qty = FLOOR(defect_qty)
    ),
    CONSTRAINT ck_result_revision CHECK (revision_no > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 14. 생산실적 정정 이력: 집계에 더하지 않으며 현재값 변경과 원자 저장한다.
CREATE TABLE production_result_correction (
    correction_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    production_result_id BIGINT UNSIGNED NOT NULL,
    from_revision INT UNSIGNED NOT NULL,
    to_revision INT UNSIGNED NOT NULL,
    before_produced_qty DECIMAL(18,6) NOT NULL,
    after_produced_qty DECIMAL(18,6) NOT NULL,
    before_good_qty DECIMAL(18,6) NOT NULL,
    after_good_qty DECIMAL(18,6) NOT NULL,
    before_defect_qty DECIMAL(18,6) NOT NULL,
    after_defect_qty DECIMAL(18,6) NOT NULL,
    before_produced_at DATETIME(6) NOT NULL,
    after_produced_at DATETIME(6) NOT NULL,
    before_worker_ref VARCHAR(64) NOT NULL,
    after_worker_ref VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (correction_id),
    UNIQUE KEY uq_result_correction (production_result_id, to_revision),
    CONSTRAINT fk_result_correction_result FOREIGN KEY (production_result_id)
        REFERENCES production_result (production_result_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_result_correction_revision CHECK (
        from_revision > 0 AND to_revision = from_revision + 1
    ),
    CONSTRAINT ck_result_correction_before_qty CHECK (
        before_produced_qty >= 0 AND before_good_qty >= 0 AND before_defect_qty >= 0
        AND before_produced_qty = before_good_qty + before_defect_qty
    ),
    CONSTRAINT ck_result_correction_after_qty CHECK (
        after_produced_qty >= 0 AND after_good_qty >= 0 AND after_defect_qty >= 0
        AND after_produced_qty = after_good_qty + after_defect_qty
    ),
    CONSTRAINT ck_result_correction_qty_ea CHECK (
        before_produced_qty = FLOOR(before_produced_qty)
        AND after_produced_qty = FLOOR(after_produced_qty)
        AND before_good_qty = FLOOR(before_good_qty)
        AND after_good_qty = FLOOR(after_good_qty)
        AND before_defect_qty = FLOOR(before_defect_qty)
        AND after_defect_qty = FLOOR(after_defect_qty)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 15. 원자재 LOT: 가용수량은 동시성 제어용 저장값이며 소비·반환 원장과 대조한다.
CREATE TABLE raw_material_lot (
    raw_lot_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_id BIGINT UNSIGNED NOT NULL,
    lot_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    unit_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    registered_qty DECIMAL(18,6) NOT NULL,
    available_qty DECIMAL(18,6) NOT NULL,
    registered_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (raw_lot_id),
    UNIQUE KEY uq_raw_lot_no (lot_no),
    KEY ix_raw_lot_item (item_id),
    CONSTRAINT fk_raw_lot_item FOREIGN KEY (item_id)
        REFERENCES item (item_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_raw_lot_quantities CHECK (
        registered_qty >= 0 AND available_qty >= 0 AND available_qty <= registered_qty
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 16. 실제 원자재 소비: 기록 오류 정정용으로 원본을 덮어쓰지 않는다.
CREATE TABLE material_consumption (
    consumption_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    work_order_id BIGINT UNSIGNED NOT NULL,
    raw_lot_id BIGINT UNSIGNED NOT NULL,
    bom_component_id BIGINT UNSIGNED NOT NULL,
    receipt_id BIGINT UNSIGNED NOT NULL,
    consumed_qty DECIMAL(18,6) NOT NULL,
    consumed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (consumption_id),
    UNIQUE KEY uq_consumption_receipt (receipt_id),
    KEY ix_consumption_wo_lot (work_order_id, raw_lot_id),
    KEY ix_consumption_raw_lot (raw_lot_id),
    KEY ix_consumption_component (bom_component_id),
    CONSTRAINT fk_consumption_work_order FOREIGN KEY (work_order_id)
        REFERENCES work_order (work_order_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_consumption_raw_lot FOREIGN KEY (raw_lot_id)
        REFERENCES raw_material_lot (raw_lot_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_consumption_component FOREIGN KEY (bom_component_id)
        REFERENCES bom_component (bom_component_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_consumption_receipt FOREIGN KEY (receipt_id)
        REFERENCES request_receipt (receipt_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_consumption_qty CHECK (consumed_qty > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 17. 실제 원자재 반환: 기록 오류 정정이 아니다. 반환 원본·Receipt·가용량 복원은 원자 처리한다.
CREATE TABLE material_return (
    return_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    consumption_id BIGINT UNSIGNED NOT NULL,
    receipt_id BIGINT UNSIGNED NOT NULL,
    returned_qty DECIMAL(18,6) NOT NULL,
    returned_at DATETIME(6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (return_id),
    UNIQUE KEY uq_return_receipt (receipt_id),
    KEY ix_return_consumption (consumption_id),
    CONSTRAINT fk_return_consumption FOREIGN KEY (consumption_id)
        REFERENCES material_consumption (consumption_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_return_receipt FOREIGN KEY (receipt_id)
        REFERENCES request_receipt (receipt_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_return_qty CHECK (returned_qty > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 18. 최종 품질검사: 회차/요청 유일성은 누적 상한 검증·물리적 실물 식별을 대신하지 않는다.
CREATE TABLE quality_inspection (
    inspection_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    finished_lot_id BIGINT UNSIGNED NOT NULL,
    receipt_id BIGINT UNSIGNED NOT NULL,
    round_no INT UNSIGNED NOT NULL,
    inspected_qty DECIMAL(18,6) NOT NULL,
    accepted_qty DECIMAL(18,6) NOT NULL,
    rejected_qty DECIMAL(18,6) NOT NULL,
    inspected_at DATETIME(6) NOT NULL,
    inspector_ref VARCHAR(64) NOT NULL,
    judgement_reason VARCHAR(500) NULL DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (inspection_id),
    UNIQUE KEY uq_inspection_round (finished_lot_id, round_no),
    UNIQUE KEY uq_inspection_receipt (receipt_id),
    CONSTRAINT fk_inspection_lot FOREIGN KEY (finished_lot_id)
        REFERENCES finished_goods_lot (finished_lot_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_inspection_receipt FOREIGN KEY (receipt_id)
        REFERENCES request_receipt (receipt_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_inspection_round CHECK (round_no > 0),
    CONSTRAINT ck_inspection_quantities CHECK (
        inspected_qty >= 0 AND accepted_qty >= 0 AND rejected_qty >= 0
        AND inspected_qty = accepted_qty + rejected_qty
    ),
    CONSTRAINT ck_inspection_qty_ea CHECK (
        inspected_qty = FLOOR(inspected_qty)
        AND accepted_qty = FLOOR(accepted_qty)
        AND rejected_qty = FLOOR(rejected_qty)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
