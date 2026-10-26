package com.jade.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 预约看货（UC-10；UC-27 凭手机号后四位查询） */
@TableName("t_customer_tag")
public class CustomerTag {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户 -> t_customer */
    private Long customerId;

    /** 标签：大客户|懂行|爽快|要还价 */
    private String tag;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
