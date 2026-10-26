package com.taehyung.mes.bom;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BomVersion {
    private Long bomVersionId;
    private Long bomId;
    private String bomCode;
    private Long productItemId;
    private String productCode;
    private String productName;
    private Long versionNo;
    private BigDecimal baseQty;
    private String productUnitCode;
    private LocalDateTime firstUsedAt;
    private boolean workOrderReferenced;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<BomComponent> components = new ArrayList<>();

    public Long getBomVersionId() { return bomVersionId; }
    public void setBomVersionId(Long bomVersionId) { this.bomVersionId = bomVersionId; }
    public Long getBomId() { return bomId; }
    public void setBomId(Long bomId) { this.bomId = bomId; }
    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public BigDecimal getBaseQty() { return baseQty; }
    public void setBaseQty(BigDecimal baseQty) { this.baseQty = baseQty; }
    public String getProductUnitCode() { return productUnitCode; }
    public void setProductUnitCode(String productUnitCode) { this.productUnitCode = productUnitCode; }
    public LocalDateTime getFirstUsedAt() { return firstUsedAt; }
    public void setFirstUsedAt(LocalDateTime firstUsedAt) { this.firstUsedAt = firstUsedAt; }
    public boolean getWorkOrderReferenced() { return workOrderReferenced; }
    public void setWorkOrderReferenced(boolean workOrderReferenced) { this.workOrderReferenced = workOrderReferenced; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<BomComponent> getComponents() { return components; }
    public void setComponents(List<BomComponent> components) { this.components = components; }

    public boolean isUsed() { return firstUsedAt != null || workOrderReferenced; }

    /** 상태와 구성을 비교하여 오래된 폼의 덮어쓰기를 거부한다. 인증 토큰이 아니다. */
    public String getEditToken() {
        StringBuilder value = new StringBuilder().append(bomVersionId).append('|').append(bomId)
                .append('|').append(productItemId).append('|').append(versionNo)
                .append('|').append(baseQty == null ? "" : baseQty.stripTrailingZeros().toPlainString())
                .append('|').append(productUnitCode).append('|').append(firstUsedAt).append('|').append(workOrderReferenced);
        for (BomComponent row : components) {
            value.append(';').append(row.getBomComponentId()).append(':').append(row.getLineNo())
                    .append(':').append(row.getMaterialItemId()).append(':').append(row.getMaterialUnitCode())
                    .append(':').append(row.getRequiredQty().stripTrailingZeros().toPlainString());
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java.", exception);
        }
    }
}

