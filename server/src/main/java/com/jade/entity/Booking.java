package com.jade.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 预定记录（UC-13 预定、UC-28 到店跟进，状态机见规约；区别于 UC-01 规则③ 预留） */
@TableName("t_booking")
public class Booking {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预定编号 */
    private String bookingNo;

    /** 货品 -> t_goods */
    private Long goodsId;

    /** 姓名 */
    private String name;

    /** 手机号（3 次额度按此计） */
    private String phone;

    /** 来源 IP（防刷：同一网络的有效预定数有上限，口径同预约） */
    private String ip;

    /** 客户 -> t_customer（已建档客户按手机号关联，散客可空） */
    private Long customerId;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 联系时间（UC-28 标记已联系时写入） */
    private LocalDateTime contactTime;

    /** 状态：待到店|已联系|已达成|已爽约|已取消 */
    private String status;

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

    public String getBookingNo() {
        return bookingNo;
    }

    public void setBookingNo(String bookingNo) {
        this.bookingNo = bookingNo;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
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

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public LocalDateTime getContactTime() {
        return contactTime;
    }

    public void setContactTime(LocalDateTime contactTime) {
        this.contactTime = contactTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
