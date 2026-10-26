package com.jade.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 客户（UC-04；标签见 t_customer_tag，介绍人自引用） */
@TableName("t_stock_log")
public class StockLog {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 货品 -> t_goods */
    private Long goodsId;

    /** 变更前状态（入库为第一条流水时为空） */
    private String fromStatus;

    /** 变更后状态（取值同 t_goods.status） */
    private String toStatus;

    /** 关联单据类型（成交/退货/调货记录/加工任务/送检记录/入库批次/寄售合同） */
    private String refType;

    /** 关联单据编号（逻辑引用，同 t_payment 口径） */
    private String refNo;

    /** 经手人 -> t_user */
    private Long operatorId;

    /** 流水时间（系统时间） */
    private LocalDateTime logTime;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public String getRefNo() {
        return refNo;
    }

    public void setRefNo(String refNo) {
        this.refNo = refNo;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getLogTime() {
        return logTime;
    }

    public void setLogTime(LocalDateTime logTime) {
        this.logTime = logTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
