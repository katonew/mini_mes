package com.taehyung.mes.process;

/** HTTP 요청 전용. 작성자·PK·시각은 입력받지 않는다. */
public class ProcessForm {
    private String processCode;
    private String processName;
    private String description;
    private Integer isActive;
    public String getProcessCode() { return processCode; }
    public void setProcessCode(String processCode) { this.processCode = processCode; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public static ProcessForm from(ManufacturingProcess process) {
        ProcessForm form = new ProcessForm();
        form.setProcessCode(process.getProcessCode()); form.setProcessName(process.getProcessName());
        form.setDescription(process.getDescription()); form.setIsActive(process.getIsActive());
        return form;
    }
}
