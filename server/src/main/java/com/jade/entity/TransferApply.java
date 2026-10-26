package com.jade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 出让申请含审核（UC-14/UC-18） */
@TableName("t_transfer_apply")
public class TransferApply {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请编号 */
    private String applyNo;

    /** 供应商 -> t_source_party（类别=供应商，含回流出让方） */
    private Long supplierId;

    /** 出让类型：玉料|成品|回流 */
    private String outType;

    /** 品名 */
    private String name;

    /** 数量 */
    private Integer quantity;

    /** 期望出让价 */
    private BigDecimal expectPrice;

    /** 描述 */
    private String description;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 状态：待审核|已通过|已驳回（状态机见 07b） */
    private String status;

    /** 审核意见（UC-18） */
    private String auditOpinion;

    /** 审核时间（UC-18） */
    private LocalDateTime auditTime;

    /** 审核人 -> t_user（UC-18） */
    private Long auditorId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String outType) {
        this.outType = outType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getExpectPrice() {
        return expectPrice;
    }

    public void setExpectPrice(BigDecimal expectPrice) {
        this.expectPrice = expectPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAuditOpinion() {
        return auditOpinion;
    }

    public void setAuditOpinion(String auditOpinion) {
        this.auditOpinion = auditOpinion;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
