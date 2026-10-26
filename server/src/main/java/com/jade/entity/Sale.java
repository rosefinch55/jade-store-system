package com.jade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 成交记录（UC-01，UC-06 统计的数据源；分期见 t_installment） */
@TableName("t_sale")
public class Sale {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 成交编号 */
    private String saleNo;

    /** 货品 -> t_goods */
    private Long goodsId;

    /** 成交发生时间（以系统时间为准） */
    private LocalDateTime saleTime;

    /** 成交价（不得低于最低售价） */
    private BigDecimal price;

    /** 支付方式：全款|定金 */
    private String payMethod;

    /** 已收金额（定金成交时为定金额） */
    private BigDecimal received;

    /** 客户 -> t_customer（散客可空） */
    private Long customerId;

    /** 经手销售 -> t_user */
    private Long operatorId;

    /** 录入时间（与发生时间分别记录） */
    private LocalDateTime recordedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSaleNo() {
        return saleNo;
    }

    public void setSaleNo(String saleNo) {
        this.saleNo = saleNo;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public LocalDateTime getSaleTime() {
        return saleTime;
    }

    public void setSaleTime(LocalDateTime saleTime) {
        this.saleTime = saleTime;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public BigDecimal getReceived() {
        return received;
    }

    public void setReceived(BigDecimal received) {
        this.received = received;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
