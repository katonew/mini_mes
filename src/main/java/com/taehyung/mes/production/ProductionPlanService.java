package com.taehyung.mes.production;

import com.taehyung.mes.common.actor.ActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionPlanService {
    private static final BigDecimal MAX_QTY = new BigDecimal("999999999999");
    private static final LocalDate MIN_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);
    private final ProductionPlanMapper mapper;
    private final ItemMapper items;
    private final ActorProvider actorProvider;

    public ProductionPlanService(ProductionPlanMapper mapper, ItemMapper items, ActorProvider actorProvider) {
        this.mapper = mapper; this.items = items; this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public List<ProductionPlan> list(String planId, String code, String name) {
        if (code == null || name == null || code.length() > 50 || name.codePointCount(0, name.length()) > 200) {
            throw rule(null, "검색은 완제품 코드 50자, 명칭 200자 이내로 입력해 주세요.");
        }
        return mapper.findAll(parseSearchId(planId), code, name);
    }

    @Transactional(readOnly = true)
    public ProductionPlan get(Long planId) {
        if (planId == null || planId <= 0) { throw new ProductionPlanNotFoundException(); }
        ProductionPlan plan = mapper.findById(planId);
        if (plan == null) { throw new ProductionPlanNotFoundException(); }
        return plan;
    }

    @Transactional(readOnly = true)
    public List<Item> products() { return mapper.findProducts(); }
    @Transactional(readOnly = true)
    public Item displayProduct(Long id) { return id == null ? null : items.findById(id); }

    @Transactional
    public Long create(ProductionPlanForm form) {
        validate(form);
        String actor = actorProvider.getActorId();
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) {
            throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다.");
        }
        // 품목 미사용 전환과 경쟁하는 신규 등록만 보호한다. BOM/Routing은 여기서 잠그거나 확정하지 않는다.
        Item product = items.findByIdForUpdate(form.getProductItemId());
        if (product == null || !"FINISHED".equals(product.getItemKind())) { throw rule("productItemId", "존재하는 완제품(FINISHED)을 선택해 주세요."); }
        if (!Integer.valueOf(1).equals(product.getIsActive())) { throw rule("productItemId", "미사용 완제품으로 생산계획을 등록할 수 없습니다."); }
        ProductionPlan plan = new ProductionPlan(); plan.setProductItemId(form.getProductItemId());
        plan.setPlannedQty(form.getPlannedQty()); plan.setPlannedDate(form.getPlannedDate()); plan.setCreatedBy(actor);
        if (mapper.insert(plan) != 1 || plan.getProductionPlanId() == null) {
            throw new DataIntegrityViolationException("Production plan insert must produce exactly one identified row.");
        }
        return plan.getProductionPlanId();
    }

    private void validate(ProductionPlanForm form) {
        if (form == null) { throw rule(null, "생산계획 입력값이 필요합니다."); }
        if (form.getProductItemId() == null || form.getProductItemId() <= 0) { throw rule("productItemId", "완제품을 선택해 주세요."); }
        BigDecimal qty = form.getPlannedQty();
        if (qty == null || qty.signum() <= 0 || qty.compareTo(MAX_QTY) > 0 || qty.stripTrailingZeros().scale() > 0) {
            throw rule("plannedQty", "계획 수량은 1~999999999999의 정수여야 합니다. 현재 DB는 소수 계획을 허용하지 않습니다.");
        }
        LocalDate date = form.getPlannedDate();
        if (date == null || date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
            throw rule("plannedDate", "계획일은 1000-01-01~9999-12-31 범위의 유효한 날짜로 입력해 주세요.");
        }
    }

    private Long parseSearchId(String value) {
        if (value == null) { throw rule(null, "계획 ID 검색값을 확인해 주세요."); }
        if (value.isEmpty()) { return null; }
        if (!value.matches("[0-9]{1,19}")) { throw rule(null, "계획 ID는 양의 정수로 입력해 주세요."); }
        try {
            long id = Long.parseLong(value);
            if (id > 0) { return id; }
        } catch (NumberFormatException exception) {
            throw rule(null, "계획 ID가 조회 가능한 정수 범위를 초과했습니다.");
        }
        throw rule(null, "계획 ID는 양의 정수로 입력해 주세요.");
    }
    private ProductionPlanRuleException rule(String field, String message) { return new ProductionPlanRuleException(field, message); }
}
