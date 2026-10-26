package com.taehyung.mes.bom;

import com.taehyung.mes.common.actor.ActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemKind;
import com.taehyung.mes.item.ItemMapper;
import com.taehyung.mes.item.ItemUnit;
import java.math.BigDecimal;
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
public class BomService {
    // Spring 폼 바인딩의 기본 컬렉션 한도와 동일한 요청 크기 제한이다.
    public static final int MAX_COMPONENTS = 256;
    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999.999999");
    private final BomMapper mapper;
    private final ItemMapper items;
    private final ActorProvider actorProvider;

    public BomService(BomMapper mapper, ItemMapper items, ActorProvider actorProvider) {
        this.mapper = mapper;
        this.items = items;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public List<BomVersion> list(String code, String name) {
        if (code == null || name == null || code.length() > 50 || name.codePointCount(0, name.length()) > 200) {
            throw rule(null, "검색은 완제품 코드 50자, 명칭 200자 이내로 입력해 주세요.");
        }
        return mapper.findAll(code, name);
    }

    @Transactional(readOnly = true)
    public Bom getHeader(Long bomId) {
        requireId(bomId);
        Bom bom = mapper.findBom(bomId);
        if (bom == null) { throw new BomNotFoundException(); }
        return bom;
    }

    @Transactional(readOnly = true)
    public BomVersion get(Long versionId) {
        requireId(versionId);
        BomVersion version = mapper.findVersion(versionId);
        if (version == null) { throw new BomNotFoundException(); }
        version.setComponents(mapper.findComponents(versionId));
        return version;
    }

    @Transactional(readOnly = true)
    public List<Item> products() { return mapper.findSelectableItems(ItemKind.FINISHED.getCode(), ItemUnit.EA.getCode()); }

    @Transactional(readOnly = true)
    public List<Item> materials() { return mapper.findSelectableItems(ItemKind.RAW.getCode(), null); }

    @Transactional(readOnly = true)
    public List<Item> displayItems(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.findItemsByIds(ids);
    }

    @Transactional
    public Long create(BomForm form) {
        validateForm(form);
        requireNewComponents(form);
        String actor = requireActor();
        Map<Long, Item> lockedItems = lockItems(form);
        validateProduct(lockedItems.get(form.getProductItemId()), true);
        validateMaterials(form, lockedItems, Map.of());
        if (mapper.existsByCode(form.getBomCode())) { throw duplicateCode(); }
        Bom bom = new Bom();
        bom.setBomCode(form.getBomCode()); bom.setProductItemId(form.getProductItemId()); bom.setCreatedBy(actor);
        try {
            requireOne(mapper.insertBom(bom));
            if (bom.getBomId() == null) { throw new DataIntegrityViolationException("Missing BOM generated ID."); }
        } catch (DuplicateKeyException exception) { throw duplicateCode(); }
        return insertVersion(bom.getBomId(), form, lockedItems, actor);
    }

    @Transactional
    public Long addVersion(Long bomId, BomForm form) {
        requireId(bomId);
        validateForm(form);
        requireNewComponents(form);
        String actor = requireActor();
        Bom bom = mapper.findBomForUpdate(bomId);
        if (bom == null) { throw new BomNotFoundException(); }
        validateHeader(bom, form);
        Map<Long, Item> lockedItems = lockItems(form);
        validateProduct(lockedItems.get(bom.getProductItemId()), true);
        validateMaterials(form, lockedItems, Map.of());
        if (mapper.existsVersion(bomId, form.getVersionNo())) { throw duplicateVersion(); }
        return insertVersion(bomId, form, lockedItems, actor);
    }

    @Transactional
    public void update(Long versionId, BomForm form) {
        requireId(versionId);
        validateForm(form);
        BomVersion located = mapper.findVersion(versionId);
        if (located == null) { throw new BomNotFoundException(); }
        // 향후 작업지시 생성도 헤더 → 버전 → 품목(PK순) 잠금 순서를 공유해야 한다.
        Bom bom = mapper.findBomForUpdate(located.getBomId());
        if (bom == null) { throw new BomNotFoundException(); }
        BomVersion original = mapper.findVersionForUpdate(versionId);
        if (original == null) { throw new BomNotFoundException(); }
        original.setBomCode(bom.getBomCode());
        original.setProductItemId(bom.getProductItemId());
        original.setWorkOrderReferenced(mapper.hasWorkOrders(versionId));
        if (original.isUsed()) { throw rule(null, "사용 이력이 있는 BOM 버전은 수정할 수 없습니다. 새 버전을 등록해 주세요."); }
        validateHeader(bom, form);
        if (!Objects.equals(original.getVersionNo(), form.getVersionNo())) {
            throw rule("versionNo", "등록된 버전 번호는 변경할 수 없습니다.");
        }
        if (original.getBaseQty().compareTo(BigDecimal.ONE) != 0 || !ItemUnit.EA.getCode().equals(original.getProductUnitCode())) {
            throw rule(null, "완제품 1 EA 기준이 아닌 기존 BOM은 조회만 가능합니다. 새 버전을 등록해 주세요.");
        }
        original.setComponents(mapper.findComponents(versionId));
        if (!Objects.equals(original.getEditToken(), form.getEditToken())) {
            throw rule(null, "다른 저장으로 BOM이 변경되었습니다. 상세 화면을 다시 조회한 후 수정해 주세요.");
        }
        Map<Long, BomComponent> existing = new HashMap<>();
        original.getComponents().forEach(row -> existing.put(row.getBomComponentId(), row));
        validateRetainedComponents(form, existing);
        Map<Long, Item> lockedItems = lockItems(form);
        validateProduct(lockedItems.get(bom.getProductItemId()), false);
        validateMaterials(form, lockedItems, existing);
        boolean adding = form.getComponents().stream().anyMatch(row -> row.getBomComponentId() == null);
        String actor = adding ? requireActor() : null;
        long line = original.getComponents().stream().mapToLong(BomComponent::getLineNo).max().orElse(0);
        long additions = form.getComponents().stream().filter(row -> row.getBomComponentId() == null).count();
        if (line + additions > MAX_UNSIGNED_INT) { throw rule(null, "구성행 번호의 저장 범위를 초과합니다."); }
        for (BomComponentForm row : form.getComponents()) {
            if (row.getBomComponentId() == null) {
                insertComponent(versionId, ++line, row, lockedItems.get(row.getMaterialItemId()), actor);
            } else if (existing.get(row.getBomComponentId()).getRequiredQty().compareTo(row.getRequiredQty()) != 0) {
                requireOne(mapper.updateQuantity(versionId, row.getBomComponentId(), row.getRequiredQty()));
            }
        }
    }

    private Long insertVersion(Long bomId, BomForm form, Map<Long, Item> lockedItems, String actor) {
        BomVersion version = new BomVersion();
        version.setBomId(bomId); version.setVersionNo(form.getVersionNo());
        version.setBaseQty(BigDecimal.ONE); version.setProductUnitCode(ItemUnit.EA.getCode()); version.setCreatedBy(actor);
        try { requireOne(mapper.insertVersion(version)); }
        catch (DuplicateKeyException exception) { throw duplicateVersion(); }
        if (version.getBomVersionId() == null) { throw new DataIntegrityViolationException("Missing BOM version generated ID."); }
        long line = 0;
        for (BomComponentForm row : form.getComponents()) {
            insertComponent(version.getBomVersionId(), ++line, row, lockedItems.get(row.getMaterialItemId()), actor);
        }
        return version.getBomVersionId();
    }

    private void insertComponent(Long versionId, long line, BomComponentForm form, Item material, String actor) {
        BomComponent row = new BomComponent();
        row.setBomVersionId(versionId); row.setLineNo(line); row.setMaterialItemId(form.getMaterialItemId());
        row.setRequiredQty(form.getRequiredQty()); row.setMaterialUnitCode(material.getUnitCode()); row.setCreatedBy(actor);
        requireOne(mapper.insertComponent(row));
    }

    private void validateForm(BomForm form) {
        if (form == null) { throw rule(null, "BOM 입력값이 필요합니다."); }
        String code = form.getBomCode();
        if (code == null || code.isBlank() || code.length() > 50 || code.codePoints().anyMatch(c -> c < 32 || c > 126)) {
            throw rule("bomCode", "BOM 코드는 표시 가능한 ASCII 문자로 1~50자 입력해 주세요.");
        }
        if (form.getProductItemId() == null || form.getProductItemId() <= 0) { throw rule("productItemId", "완제품을 선택해 주세요."); }
        if (form.getVersionNo() == null || form.getVersionNo() <= 0 || form.getVersionNo() > MAX_UNSIGNED_INT) {
            throw rule("versionNo", "버전 번호는 1~4294967295의 정수여야 합니다.");
        }
        if (form.getComponents() == null || form.getComponents().isEmpty()) { throw rule(null, "구성 원자재를 1개 이상 등록해 주세요."); }
        if (form.getComponents().size() > MAX_COMPONENTS) { throw rule(null, "한 번에 저장할 수 있는 구성행은 256개입니다."); }
        Set<Long> materialIds = new HashSet<>();
        for (int i = 0; i < form.getComponents().size(); i++) {
            BomComponentForm row = form.getComponents().get(i);
            if (row == null || row.getMaterialItemId() == null || row.getMaterialItemId() <= 0) {
                throw rule(null, (i + 1) + "행의 원자재를 선택해 주세요.");
            }
            if (!materialIds.add(row.getMaterialItemId())) { throw rule("components[" + i + "].materialItemId", "동일 버전에는 같은 원자재를 중복 등록할 수 없습니다."); }
            BigDecimal qty = row.getRequiredQty();
            if (qty == null || qty.signum() <= 0 || qty.compareTo(MAX_QUANTITY) > 0 || qty.stripTrailingZeros().scale() > 6) {
                throw rule("components[" + i + "].requiredQty", "소요량은 0보다 크고 정수부 12자리·소수부 6자리 이내여야 합니다.");
            }
        }
    }

    private Map<Long, Item> lockItems(BomForm form) {
        Set<Long> ids = new TreeSet<>();
        ids.add(form.getProductItemId());
        form.getComponents().forEach(row -> ids.add(row.getMaterialItemId()));
        Map<Long, Item> result = new HashMap<>();
        for (Long id : ids) { result.put(id, items.findByIdForUpdate(id)); }
        return result;
    }

    private void validateProduct(Item item, boolean registering) {
        if (item == null || !ItemKind.FINISHED.getCode().equals(item.getItemKind()) || !ItemUnit.EA.getCode().equals(item.getUnitCode())) {
            throw rule("productItemId", "EA 단위의 완제품(FINISHED)을 선택해 주세요.");
        }
        if (registering && !Integer.valueOf(1).equals(item.getIsActive())) { throw rule("productItemId", "미사용 완제품으로 새 BOM 버전을 등록할 수 없습니다."); }
    }

    private void validateMaterials(BomForm form, Map<Long, Item> lockedItems, Map<Long, BomComponent> existing) {
        for (int i = 0; i < form.getComponents().size(); i++) {
            BomComponentForm row = form.getComponents().get(i);
            String field = "components[" + i + "]";
            Item material = lockedItems.get(row.getMaterialItemId());
            if (material == null || !ItemKind.RAW.getCode().equals(material.getItemKind()) || !ItemUnit.isSupported(material.getUnitCode())) {
                throw rule(field + ".materialItemId", "EA, M, G 단위의 원자재(RAW)를 선택해 주세요.");
            }
            BomComponent old = row.getBomComponentId() == null ? null : existing.get(row.getBomComponentId());
            if (old == null && !Integer.valueOf(1).equals(material.getIsActive())) {
                throw rule(field + ".materialItemId", "미사용 원자재는 새 구성에 추가할 수 없습니다.");
            }
            if (old != null && !Objects.equals(old.getMaterialUnitCode(), material.getUnitCode())) {
                throw rule(field + ".materialItemId", "저장된 BOM 단위와 현재 품목 단위가 다릅니다. 기준정보를 확인해 주세요.");
            }
            if (ItemUnit.EA.getCode().equals(material.getUnitCode()) && row.getRequiredQty().stripTrailingZeros().scale() > 0) {
                throw rule(field + ".requiredQty", "EA 단위의 소요량은 정수여야 합니다.");
            }
        }
    }

    private void validateRetainedComponents(BomForm form, Map<Long, BomComponent> existing) {
        Set<Long> retained = new HashSet<>();
        for (BomComponentForm row : form.getComponents()) {
            if (row.getBomComponentId() == null) { continue; }
            BomComponent old = existing.get(row.getBomComponentId());
            if (old == null || !retained.add(row.getBomComponentId()) || !Objects.equals(old.getMaterialItemId(), row.getMaterialItemId())) {
                throw rule(null, "기존 구성 원자재를 교체하거나 다른 버전의 구성행을 사용할 수 없습니다. 새 버전을 등록해 주세요.");
            }
        }
        if (!retained.equals(existing.keySet())) { throw rule(null, "저장된 구성 원자재는 제거할 수 없습니다. 새 버전을 등록해 주세요."); }
    }

    private void requireNewComponents(BomForm form) {
        if (form.getComponents().stream().anyMatch(row -> row.getBomComponentId() != null)) { throw rule(null, "신규 버전은 새로운 구성행으로 등록해야 합니다."); }
    }

    private void validateHeader(Bom bom, BomForm form) {
        if (!Objects.equals(bom.getBomCode(), form.getBomCode()) || !Objects.equals(bom.getProductItemId(), form.getProductItemId())) {
            throw rule(null, "등록된 BOM 코드와 완제품은 변경할 수 없습니다.");
        }
    }

    private String requireActor() {
        String actor = actorProvider.getActorId();
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) {
            throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다.");
        }
        return actor;
    }

    private void requireId(Long id) { if (id == null || id <= 0) { throw new BomNotFoundException(); } }
    private void requireOne(int count) { if (count != 1) { throw new DataIntegrityViolationException("BOM write must affect exactly one row."); } }
    private BomRuleException duplicateCode() { return rule("bomCode", "중복된 BOM 코드입니다. 기존 BOM에 버전을 추가하거나 다른 코드를 입력해 주세요."); }
    private BomRuleException duplicateVersion() { return rule("versionNo", "해당 BOM에 같은 버전 번호가 이미 등록되어 있습니다."); }
    private BomRuleException rule(String field, String message) { return new BomRuleException(field, message); }
}
