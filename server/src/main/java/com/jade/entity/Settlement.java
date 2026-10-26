package com.jade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 出让申请含审核（UC-14/UC-18） */
@TableName("t_settlement")
public class Settlement {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 结算编号 */
    private String settleNo;

    /** 结算往来方 -> t_source_party */
    private Long partyId;

    /** 结算类型：调货|寄售 */
    private String settleType;

    /** 调货记录 -> t_transfer（结算类型=调货时非空） */
    private Long transferId;

    /** 寄售明细 -> t_consignment_item（结算类型=寄售时非空） */
    private Long consignItemId;

    /** 结算金额 */
    private BigDecimal amount;

    /** 结算方式：现金|转账|冲抵 */
    private String settleMethod;

    /** 结算时间 */
    private LocalDateTime settleTime;

    /** 经手人 -> t_user */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSettleNo() {
        return settleNo;
    }

    public void setSettleNo(String settleNo) {
        this.settleNo = settleNo;
    }

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long partyId) {
        this.partyId = partyId;
    }

    public String getSettleType() {
        return settleType;
    }

    public void setSettleType(String settleType) {
        this.settleType = settleType;
    }

    public Long getTransferId() {
        return transferId;
    }

    public void setTransferId(Long transferId) {
        this.transferId = transferId;
    }

    public Long getConsignItemId() {
        return consignItemId;
    }

    public void setConsignItemId(Long consignItemId) {
        this.consignItemId = consignItemId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getSettleMethod() {
        return settleMethod;
    }

    public void setSettleMethod(String settleMethod) {
        this.settleMethod = settleMethod;
    }

    public LocalDateTime getSettleTime() {
        return settleTime;
    }

    public void setSettleTime(LocalDateTime settleTime) {
        this.settleTime = settleTime;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
