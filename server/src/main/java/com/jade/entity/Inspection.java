package com.jade.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 送检记录（UC-05） */
@TableName("t_inspection")
public class Inspection {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 送检时间 */
    private LocalDateTime inspTime;

    /** 货品 -> t_goods */
    private Long goodsId;

    /** 送检机构 -> t_cert_org */
    private Long orgId;

    /** 送检人 -> t_user */
    private Long submitterId;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getInspTime() {
        return inspTime;
    }

    public void setInspTime(LocalDateTime inspTime) {
        this.inspTime = inspTime;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getSubmitterId() {
        return submitterId;
    }

    public void setSubmitterId(Long submitterId) {
        this.submitterId = submitterId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
