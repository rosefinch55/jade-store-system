package com.jade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 寄售明细（UC-21，一合同多件货品；一货品在档至多一条明细，重复寄售前须结清原合同） */
@TableName("t_consignment_item")
public class ConsignmentItem {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 寄售合同 -> t_consignment */
    private Long consignmentId;

    /** 货品 -> t_goods（入库来源类型=寄售，成本=寄售价） */
    private Long goodsId;

    /** 寄售价（售出后按此价与供应商结算） */
    private BigDecimal consignPrice;

    /** 结算状态：未结算|已结算（UC-22） */
    private String settleStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConsignmentId() {
        return consignmentId;
    }

    public void setConsignmentId(Long consignmentId) {
        this.consignmentId = consignmentId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public BigDecimal getConsignPrice() {
        return consignPrice;
    }

    public void setConsignPrice(BigDecimal consignPrice) {
        this.consignPrice = consignPrice;
    }

    public String getSettleStatus() {
        return settleStatus;
    }

    public void setSettleStatus(String settleStatus) {
        this.settleStatus = settleStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
