package com.taehyung.mes.item;

import com.taehyung.mes.common.actor.ActorProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {
    private final ItemMapper mapper;
    private final ActorProvider actorProvider;

    public ItemService(ItemMapper mapper, ActorProvider actorProvider) {
        this.mapper = mapper;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public List<Item> list(String code, String name) {
        if (code == null || name == null || code.length() > 50 || name.codePointCount(0, name.length()) > 200) {
            throw new ItemRuleException(null, "검색은 품목코드 50자, 품목명 200자 이내로 입력해 주세요.");
        }
        return mapper.findAll(code, name);
    }

    @Transactional(readOnly = true)
    public Item get(Long itemId) {
        requireId(itemId);
        Item item = mapper.findById(itemId);
        if (item == null) { throw new ItemNotFoundException(); }
        return item;
    }

    @Transactional
    public Long create(ItemForm form) {
        validateEditable(form);
        requireText(form.getItemCode(), 50, "itemCode", "품목코드");
        if (form.getItemCode().codePoints().anyMatch(c -> c < 32 || c > 126)) {
            throw new ItemRuleException("itemCode", "품목코드는 표시 가능한 ASCII 문자로 입력해 주세요.");
        }
        if (!ItemKind.canRegister(form.getItemKind())) {
            throw new ItemRuleException("itemKind", "등록 가능한 품목구분은 원자재와 완제품입니다.");
        }
        if (!ItemUnit.isSupported(form.getUnitCode())) {
            throw new ItemRuleException("unitCode", "관리단위는 EA, M, G 중에서 선택해 주세요.");
        }
        String actorId = actorProvider.getActorId();
        if (actorId == null || actorId.isBlank()) {
            throw new ItemRuleException(null, "작성자 설정이 없어 등록할 수 없습니다. MES_ACTOR_ID 설정을 확인해 주세요.");
        }
        if (actorId.codePointCount(0, actorId.length()) > 64) {
            throw new ItemRuleException(null, "MES_ACTOR_ID는 64자 이하여야 합니다.");
        }
        if (mapper.existsByCode(form.getItemCode())) { throw duplicateCode(); }

        Item item = new Item();
        item.setItemCode(form.getItemCode());
        item.setItemName(form.getItemName());
        item.setItemKind(form.getItemKind());
        item.setUnitCode(form.getUnitCode());
        item.setIsActive(form.getIsActive());
        item.setCreatedBy(actorId);
        try {
            if (mapper.insert(item) != 1 || item.getItemId() == null) {
                throw new DataIntegrityViolationException("Item insert did not produce exactly one identified row.");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateCode();
        }
        return item.getItemId();
    }

    @Transactional
    public void update(Long itemId, ItemForm form) {
        requireId(itemId);
        validateEditable(form);
        Item original = mapper.findByIdForUpdate(itemId);
        if (original == null) { throw new ItemNotFoundException(); }
        if (!Objects.equals(original.getItemCode(), form.getItemCode())
                || !Objects.equals(original.getItemKind(), form.getItemKind())
                || !Objects.equals(original.getUnitCode(), form.getUnitCode())) {
            throw new ItemRuleException(null, "품목코드·품목구분·관리단위는 등록 후 변경할 수 없습니다.");
        }
        if (mapper.updateEditable(itemId, form.getItemName(), form.getIsActive()) != 1) {
            throw new DataIntegrityViolationException("Item update did not affect exactly one row.");
        }
    }

    private void validateEditable(ItemForm form) {
        if (form == null) { throw new ItemRuleException(null, "품목 입력값이 필요합니다."); }
        requireText(form.getItemName(), 200, "itemName", "품목명");
        if (form.getIsActive() == null || (form.getIsActive() != 0 && form.getIsActive() != 1)) {
            throw new ItemRuleException("isActive", "사용 여부를 선택해 주세요.");
        }
    }

    private void requireText(String value, int max, String field, String label) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > max) {
            throw new ItemRuleException(field, label + "은(는) 필수이며 " + max + "자 이하여야 합니다.");
        }
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) { throw new ItemNotFoundException(); }
    }

    private ItemRuleException duplicateCode() {
        return new ItemRuleException("itemCode", "중복된 품목코드입니다. 다른 코드를 입력해 주세요.");
    }
}
