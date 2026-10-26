package com.taehyung.mes.process;

import com.taehyung.mes.common.actor.ActorProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessService {
    private final ProcessMapper mapper;
    private final ActorProvider actorProvider;

    public ProcessService(ProcessMapper mapper, ActorProvider actorProvider) {
        this.mapper = mapper; this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public List<ManufacturingProcess> list(String code, String name) {
        if (code == null || name == null || code.length() > 50 || name.codePointCount(0, name.length()) > 200) {
            throw new ProcessRuleException(null, "검색은 공정코드 50자, 공정명 200자 이내로 입력해 주세요.");
        }
        return mapper.findAll(code, name);
    }

    @Transactional(readOnly = true)
    public ManufacturingProcess get(Long processId) {
        requireId(processId);
        ManufacturingProcess process = mapper.findById(processId);
        if (process == null) { throw new ProcessNotFoundException(); }
        return process;
    }

    @Transactional
    public Long create(ProcessForm form) {
        validateEditable(form);
        requireText(form.getProcessCode(), 50, "processCode", "공정코드");
        if (form.getProcessCode().codePoints().anyMatch(c -> c < 32 || c > 126)) {
            throw new ProcessRuleException("processCode", "공정코드는 표시 가능한 ASCII 문자로 입력해 주세요.");
        }
        String actor = actorProvider.getActorId();
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) {
            throw new ProcessRuleException(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다.");
        }
        if (mapper.existsByCode(form.getProcessCode())) { throw duplicateCode(); }
        ManufacturingProcess process = new ManufacturingProcess();
        process.setProcessCode(form.getProcessCode()); process.setProcessName(form.getProcessName());
        process.setDescription(form.getDescription()); process.setIsActive(form.getIsActive()); process.setCreatedBy(actor);
        try {
            if (mapper.insert(process) != 1 || process.getProcessId() == null) {
                throw new DataIntegrityViolationException("Process insert did not produce exactly one identified row.");
            }
        } catch (DuplicateKeyException exception) { throw duplicateCode(); }
        return process.getProcessId();
    }

    @Transactional
    public void update(Long processId, ProcessForm form) {
        requireId(processId);
        validateEditable(form);
        ManufacturingProcess original = mapper.findByIdForUpdate(processId);
        if (original == null) { throw new ProcessNotFoundException(); }
        if (!Objects.equals(original.getProcessCode(), form.getProcessCode())) {
            throw new ProcessRuleException("processCode", "공정코드는 등록 후 변경할 수 없습니다.");
        }
        // 현재 기준정보만 변경한다. Routing의 공정 FK·순서·명칭 스냅샷은 보존한다.
        if (mapper.updateEditable(processId, form.getProcessName(), form.getDescription(), form.getIsActive()) != 1) {
            throw new DataIntegrityViolationException("Process update did not affect exactly one row.");
        }
    }

    private void validateEditable(ProcessForm form) {
        if (form == null) { throw new ProcessRuleException(null, "공정 입력값이 필요합니다."); }
        requireText(form.getProcessName(), 200, "processName", "공정명");
        if (form.getDescription() != null && form.getDescription().codePointCount(0, form.getDescription().length()) > 500) {
            throw new ProcessRuleException("description", "공정 설명은 500자 이하여야 합니다.");
        }
        if (form.getIsActive() == null || (form.getIsActive() != 0 && form.getIsActive() != 1)) {
            throw new ProcessRuleException("isActive", "사용 여부를 선택해 주세요.");
        }
    }

    private void requireText(String value, int max, String field, String label) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > max) {
            throw new ProcessRuleException(field, label + "은(는) 필수이며 " + max + "자 이하여야 합니다.");
        }
    }
    private void requireId(Long id) { if (id == null || id <= 0) { throw new ProcessNotFoundException(); } }
    private ProcessRuleException duplicateCode() { return new ProcessRuleException("processCode", "중복된 공정코드입니다. 다른 코드를 입력해 주세요."); }
}
