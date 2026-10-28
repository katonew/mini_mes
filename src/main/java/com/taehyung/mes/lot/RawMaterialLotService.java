package com.taehyung.mes.lot;

import com.taehyung.mes.common.actor.ActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemMapper;
import com.taehyung.mes.item.ItemUnit;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RawMaterialLotService {
    private static final BigDecimal MAX_QTY = new BigDecimal("999999999999.999999");
    private final RawMaterialLotMapper mapper;
    private final ItemMapper items;
    private final ActorProvider actors;

    public RawMaterialLotService(RawMaterialLotMapper mapper, ItemMapper items, ActorProvider actors) {
        this.mapper = mapper; this.items = items; this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<RawMaterialLot> list(String lotNo, String code, String name) {
        if (lotNo == null || code == null || name == null || lotNo.length() > 64 || code.length() > 50
                || name.codePointCount(0, name.length()) > 200) {
            throw rule(null, "검색은 LOT 번호 64자, 원자재 코드 50자, 명칭 200자 이내로 입력해 주세요.");
        }
        return mapper.findAll(lotNo, code, name);
    }

    @Transactional(readOnly = true)
    public List<Item> choices() { return mapper.findActiveRawItems(); }

    @Transactional(readOnly = true)
    public RawMaterialLot get(Long lotId) {
        if (lotId == null || lotId <= 0) { throw new RawMaterialLotNotFoundException(); }
        RawMaterialLot lot = mapper.findById(lotId);
        if (lot == null) { throw new RawMaterialLotNotFoundException(); }
        return lot;
    }

    @Transactional
    public Long create(RawMaterialLotForm form) {
        validateForm(form);
        String actor = actors.getActorId();
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) {
            throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다.");
        }
        Item raw = items.findByIdForUpdate(form.getItemId());
        if (raw == null || !"RAW".equals(raw.getItemKind()) || !Integer.valueOf(1).equals(raw.getIsActive())) {
            throw rule("itemId", "현재 사용 중인 RAW 원자재만 신규 LOT에 사용할 수 있습니다.");
        }
        if (!ItemUnit.isSupported(raw.getUnitCode())) { throw rule("itemId", "원자재의 관리단위는 EA, M, G여야 합니다."); }
        if ("EA".equals(raw.getUnitCode()) && form.getRegisteredQty().stripTrailingZeros().scale() > 0) {
            throw rule("registeredQty", "EA 원자재의 최초 등록수량은 1 이상의 정수여야 합니다.");
        }
        if (mapper.existsByLotNo(form.getLotNo())) { throw duplicate(); }
        RawMaterialLot lot = new RawMaterialLot(); lot.setItemId(raw.getItemId()); lot.setLotNo(form.getLotNo());
        lot.setUnitCode(raw.getUnitCode()); lot.setRegisteredQty(form.getRegisteredQty()); lot.setAvailableQty(form.getRegisteredQty()); lot.setCreatedBy(actor);
        try {
            if (mapper.insert(lot) != 1 || lot.getRawLotId() == null) {
                throw new DataIntegrityViolationException("Raw material lot insert must produce exactly one identified row.");
            }
        } catch (DuplicateKeyException exception) { throw duplicate(); }
        return lot.getRawLotId();
    }

    private void validateForm(RawMaterialLotForm form) {
        if (form == null) { throw rule(null, "원자재 LOT 입력값이 필요합니다."); }
        if (form.getItemId() == null || form.getItemId() <= 0) { throw rule("itemId", "원자재 품목을 선택해 주세요."); }
        String number = form.getLotNo();
        if (number == null || number.isBlank() || number.length() > 64 || number.codePoints().anyMatch(c -> c < 32 || c > 126)) {
            throw rule("lotNo", "LOT 번호는 공백만으로 구성되지 않은 표시 가능한 ASCII 1~64자로 입력해 주세요.");
        }
        BigDecimal qty = form.getRegisteredQty();
        if (qty == null || qty.signum() <= 0 || qty.compareTo(MAX_QTY) > 0 || qty.scale() > 6) {
            throw rule("registeredQty", "최초 등록수량은 0보다 크고 999999999999.999999 이하이며 소수 6자리 이내여야 합니다.");
        }
    }
    private RawMaterialLotRuleException duplicate() { return rule("lotNo", "이미 사용 중인 LOT 번호입니다. 원자재 품목이 달라도 같은 LOT 번호를 사용할 수 없습니다."); }
    private RawMaterialLotRuleException rule(String field, String message) { return new RawMaterialLotRuleException(field, message); }
}
