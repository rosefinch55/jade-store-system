package com.jade.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 预约看货（UC-10；UC-27 凭手机号后四位查询） */
@TableName("t_reservation")
public class Reservation {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预约编号 */
    private String resvNo;

    /** 货品 -> t_goods（预约查看的目标） */
    private Long goodsId;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 提交来源 IP（限流与审计） */
    private String ip;

    /** 到店时间（半小时对齐） */
    private LocalDateTime expectSlot;

    /** 违约是否已豁免：0 否 1 是 */
    private Integer exempt;

    /** 解除操作人 -> t_user */
    private Long exemptBy;

    /** 解除时间 */
    private LocalDateTime exemptAt;

    /** 状态：待确认|已到店|已取消|已失效 */
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

    public String getResvNo() {
        return resvNo;
    }

    public void setResvNo(String resvNo) {
        this.resvNo = resvNo;
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

    public LocalDateTime getExpectSlot() {
        return expectSlot;
    }

    public void setExpectSlot(LocalDateTime expectSlot) {
        this.expectSlot = expectSlot;
    }

    public Integer getExempt() {
        return exempt;
    }

    public void setExempt(Integer exempt) {
        this.exempt = exempt;
    }

    public Long getExemptBy() {
        return exemptBy;
    }

    public void setExemptBy(Long exemptBy) {
        this.exemptBy = exemptBy;
    }

    public LocalDateTime getExemptAt() {
        return exemptAt;
    }

    public void setExemptAt(LocalDateTime exemptAt) {
        this.exemptAt = exemptAt;
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
