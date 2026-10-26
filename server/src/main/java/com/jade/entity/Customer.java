package com.jade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 客户（UC-04；标签见 t_customer_tag，介绍人自引用） */
@TableName("t_customer")
public class Customer {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编号（系统生成） */
    private String customerNo;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 微信号 */
    private String wechat;

    /** 来源渠道：闲鱼|微信朋友圈|朋友介绍|线下|直播|老客户复购|其他 */
    private String sourceChannel;

    /** 介绍人 -> t_customer（来源渠道为朋友介绍时，UC-04） */
    private Long introducerId;

    /** 偏好品类 */
    private String prefCategory;

    /** 偏好圈口/尺寸 */
    private String prefSize;

    /** 偏好预算 */
    private BigDecimal prefBudget;

    /** 备注（含跟进记录摘要） */
    private String remark;

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

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWechat() {
        return wechat;
    }

    public void setWechat(String wechat) {
        this.wechat = wechat;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public Long getIntroducerId() {
        return introducerId;
    }

    public void setIntroducerId(Long introducerId) {
        this.introducerId = introducerId;
    }

    public String getPrefCategory() {
        return prefCategory;
    }

    public void setPrefCategory(String prefCategory) {
        this.prefCategory = prefCategory;
    }

    public String getPrefSize() {
        return prefSize;
    }

    public void setPrefSize(String prefSize) {
        this.prefSize = prefSize;
    }

    public BigDecimal getPrefBudget() {
        return prefBudget;
    }

    public void setPrefBudget(BigDecimal prefBudget) {
        this.prefBudget = prefBudget;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
