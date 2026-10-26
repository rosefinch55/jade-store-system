package com.jade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 货品类别（UC-07） */
@TableName("t_payment")
public class Payment {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 记录编号 */
    private String recordNo;

    /** 业务类型：采购货款|调货货款|加工工费|寄售货款 */
    private String bizType;

    /** 关联单据类型（入库批次/调货记录/加工任务/台账结算） */
    private String relatedType;

    /** 关联单据编号（逻辑引用，不建硬外键） */
    private String relatedNo;

    /** 金额 */
    private BigDecimal amount;

    /** 收付方向：支付|收取 */
    private String direction;

    /** 经手人 -> t_user */
    private Long operatorId;

    /** 时间 */
    private LocalDateTime payTime;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(String recordNo) {
        this.recordNo = recordNo;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public void setRelatedType(String relatedType) {
        this.relatedType = relatedType;
    }

    public String getRelatedNo() {
        return relatedNo;
    }

    public void setRelatedNo(String relatedNo) {
        this.relatedNo = relatedNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
