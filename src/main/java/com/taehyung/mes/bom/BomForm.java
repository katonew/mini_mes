package com.taehyung.mes.bom;

import java.util.ArrayList;
import java.util.List;

public class BomForm {
    private String bomCode;
    private Long productItemId;
    private Long versionNo;
    private String editToken;
    private List<BomComponentForm> components = new ArrayList<>();

    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public String getEditToken() { return editToken; }
    public void setEditToken(String editToken) { this.editToken = editToken; }
    public List<BomComponentForm> getComponents() { return components; }
    public void setComponents(List<BomComponentForm> components) { this.components = components; }

    public static BomForm from(BomVersion version) {
        BomForm form = new BomForm();
        form.setBomCode(version.getBomCode());
        form.setProductItemId(version.getProductItemId());
        form.setVersionNo(version.getVersionNo());
        form.setEditToken(version.getEditToken());
        for (BomComponent row : version.getComponents()) {
            BomComponentForm component = new BomComponentForm();
            component.setBomComponentId(row.getBomComponentId());
            component.setMaterialItemId(row.getMaterialItemId());
            component.setRequiredQty(row.getRequiredQty());
            form.getComponents().add(component);
        }
        return form;
    }
}

