package com.taehyung.mes.routing;

import com.taehyung.mes.common.actor.ActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemMapper;
import com.taehyung.mes.process.ManufacturingProcess;
import com.taehyung.mes.process.ProcessMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoutingService {
    public static final int MAX_STEPS = 256; // 기존 Spring 폼 컬렉션 바인딩 한도와 동일하다.
    private static final long MAX_UINT = 4_294_967_295L;
    private final RoutingMapper mapper;
    private final ItemMapper items;
    private final ProcessMapper processes;
    private final ActorProvider actorProvider;

    public RoutingService(RoutingMapper mapper, ItemMapper items, ProcessMapper processes, ActorProvider actorProvider) {
        this.mapper = mapper; this.items = items; this.processes = processes; this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public List<RoutingRevision> list(String code, String name) {
        if (code == null || name == null || code.length() > 50 || name.codePointCount(0, name.length()) > 200) {
            throw rule(null, "검색은 완제품 코드 50자, 명칭 200자 이내로 입력해 주세요.");
        }
        return mapper.findAll(code, name);
    }

    @Transactional(readOnly = true)
    public Routing getHeader(Long routingId) {
        requireId(routingId);
        Routing routing = mapper.findRouting(routingId);
        if (routing == null) { throw new RoutingNotFoundException(); }
        return routing;
    }

    @Transactional(readOnly = true)
    public RoutingRevision get(Long revisionId) {
        requireId(revisionId);
        RoutingRevision revision = mapper.findRevision(revisionId);
        if (revision == null) { throw new RoutingNotFoundException(); }
        revision.setSteps(mapper.findSteps(revisionId));
        return revision;
    }

    @Transactional(readOnly = true)
    public List<Item> products() { return mapper.findProducts(); }
    @Transactional(readOnly = true)
    public List<ManufacturingProcess> processes() { return mapper.findProcesses(); }
    @Transactional(readOnly = true)
    public Item displayProduct(Long id) { return id == null ? null : items.findById(id); }
    @Transactional(readOnly = true)
    public List<ManufacturingProcess> displayProcesses(List<Long> ids) { return ids.isEmpty() ? List.of() : mapper.findProcessesByIds(ids); }

    @Transactional
    public Long create(RoutingForm form) {
        validate(form);
        String actor = requireActor();
        validateProduct(form.getProductItemId());
        Map<Long, ManufacturingProcess> locked = lockProcesses(form);
        if (mapper.existsByCode(form.getRoutingCode())) { throw duplicateCode(); }
        Routing routing = new Routing(); routing.setRoutingCode(form.getRoutingCode());
        routing.setProductItemId(form.getProductItemId()); routing.setCreatedBy(actor);
        try { requireOne(mapper.insertRouting(routing)); }
        catch (DuplicateKeyException exception) { throw duplicateCode(); }
        if (routing.getRoutingId() == null) { throw new DataIntegrityViolationException("Missing routing ID."); }
        return insertRevision(routing.getRoutingId(), form, locked, actor);
    }

    @Transactional
    public Long addRevision(Long routingId, RoutingForm form) {
        requireId(routingId); validate(form);
        String actor = requireActor();
        Routing routing = mapper.findRoutingForUpdate(routingId);
        if (routing == null) { throw new RoutingNotFoundException(); }
        if (!Objects.equals(routing.getRoutingCode(), form.getRoutingCode()) || !Objects.equals(routing.getProductItemId(), form.getProductItemId())) {
            throw rule(null, "기존 Routing 코드와 완제품은 변경할 수 없습니다.");
        }
        validateProduct(routing.getProductItemId());
        Map<Long, ManufacturingProcess> locked = lockProcesses(form);
        if (mapper.existsRevision(routingId, form.getRevisionNo())) { throw duplicateRevision(); }
        return insertRevision(routingId, form, locked, actor);
    }

    private Long insertRevision(Long routingId, RoutingForm form, Map<Long, ManufacturingProcess> locked, String actor) {
        RoutingRevision revision = new RoutingRevision(); revision.setRoutingId(routingId);
        revision.setRevisionNo(form.getRevisionNo()); revision.setCreatedBy(actor);
        try { requireOne(mapper.insertRevision(revision)); }
        catch (DuplicateKeyException exception) { throw duplicateRevision(); }
        if (revision.getRoutingRevisionId() == null) { throw new DataIntegrityViolationException("Missing routing revision ID."); }
        for (RoutingStepForm input : form.getSteps()) {
            RoutingStep row = new RoutingStep(); row.setRoutingRevisionId(revision.getRoutingRevisionId());
            row.setProcessId(input.getProcessId()); row.setStepNo(input.getStepNo()); row.setCreatedBy(actor);
            row.setProcessNameSnapshot(locked.get(input.getProcessId()).getProcessName());
            requireOne(mapper.insertStep(row));
        }
        return revision.getRoutingRevisionId();
    }

    private void validateProduct(Long id) {
        Item product = items.findByIdForUpdate(id);
        if (product == null || !"FINISHED".equals(product.getItemKind())) { throw rule("productItemId", "존재하는 완제품(FINISHED)을 선택해 주세요."); }
        if (!Integer.valueOf(1).equals(product.getIsActive())) { throw rule("productItemId", "미사용 완제품으로 새 Routing 개정을 등록할 수 없습니다."); }
    }

    private Map<Long, ManufacturingProcess> lockProcesses(RoutingForm form) {
        Set<Long> ids = new TreeSet<>(); form.getSteps().forEach(row -> ids.add(row.getProcessId()));
        Map<Long, ManufacturingProcess> locked = new HashMap<>();
        for (Long id : ids) { locked.put(id, processes.findByIdForUpdate(id)); }
        for (int i = 0; i < form.getSteps().size(); i++) {
            ManufacturingProcess process = locked.get(form.getSteps().get(i).getProcessId());
            if (process == null || !Integer.valueOf(1).equals(process.getIsActive())) {
                throw rule("steps[" + i + "].processId", "존재하는 사용 공정만 신규 개정에 포함할 수 있습니다.");
            }
            String name = process.getProcessName();
            if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 200) {
                throw rule(null, "선택 공정의 명칭이 유효하지 않습니다. 공정 기준정보를 확인해 주세요.");
            }
        }
        return locked;
    }

    private void validate(RoutingForm form) {
        if (form == null) { throw rule(null, "Routing 입력값이 필요합니다."); }
        String code = form.getRoutingCode();
        if (code == null || code.isBlank() || code.length() > 50 || code.codePoints().anyMatch(c -> c < 32 || c > 126)) {
            throw rule("routingCode", "Routing 코드는 표시 가능한 ASCII 문자 1~50자로 입력해 주세요.");
        }
        if (form.getProductItemId() == null || form.getProductItemId() <= 0) { throw rule("productItemId", "완제품을 선택해 주세요."); }
        if (!validUint(form.getRevisionNo())) { throw rule("revisionNo", "개정 번호는 1~4294967295 정수여야 합니다."); }
        if (form.getSteps() == null || form.getSteps().isEmpty() || form.getSteps().size() > MAX_STEPS) { throw rule(null, "공정 구성은 1~256행으로 입력해 주세요."); }
        Set<Long> numbers = new HashSet<>();
        for (int i = 0; i < form.getSteps().size(); i++) {
            RoutingStepForm row = form.getSteps().get(i);
            if (row == null || row.getProcessId() == null || row.getProcessId() <= 0) { throw rule(null, (i + 1) + "행의 공정을 선택해 주세요."); }
            if (!validUint(row.getStepNo()) || !numbers.add(row.getStepNo())) { throw rule("steps[" + i + "].stepNo", "공정 순서는 중복되지 않는 1~4294967295 정수여야 합니다."); }
        }
    }

    private boolean validUint(Long value) { return value != null && value > 0 && value <= MAX_UINT; }
    private String requireActor() {
        String actor = actorProvider.getActorId();
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) { throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다."); }
        return actor;
    }
    private void requireId(Long id) { if (id == null || id <= 0) { throw new RoutingNotFoundException(); } }
    private void requireOne(int count) { if (count != 1) { throw new DataIntegrityViolationException("Routing write must affect exactly one row."); } }
    private RoutingRuleException duplicateCode() { return rule("routingCode", "중복된 Routing 코드입니다. 기존 Routing에 개정을 추가해 주세요."); }
    private RoutingRuleException duplicateRevision() { return rule("revisionNo", "동일 Routing에 중복된 개정 번호가 있습니다."); }
    private RoutingRuleException rule(String field, String message) { return new RoutingRuleException(field, message); }
}
