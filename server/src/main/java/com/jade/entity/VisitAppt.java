package com.jade.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 上门预约（UC-15，规则①要求关联已通过的出让申请或已确认的完工交付申请） */
@TableName("t_visit_appt")
public class VisitAppt {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预约编号 */
    private String apptNo;

    /** 预约方（供应商/加工师傅） */
    private String apptParty;

    /** 事项（上门交接内容） */
    private String matter;

    /** 期望上门日期（UC-15 规则②判重与跟客户预约比对的依据，2026-10-26 起结构化） */
    private LocalDate expectDate;

    /** 期望时段起 */
    private LocalTime startTime;

    /** 期望时段止（服务端按起 + 1 小时写入） */
    private LocalTime endTime;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 状态：待上门|已完成|已取消 */
    private String status;

    /** 关联出让申请 -> t_transfer_apply（UC-15 规则①，二选一必有其一） */
    private Long transferApplyId;

    /** 关联完工交付申请 -> t_delivery_apply（UC-15 规则①，二选一必有其一） */
    private Long deliveryApplyId;

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

    public String getApptNo() {
        return apptNo;
    }

    public void setApptNo(String apptNo) {
        this.apptNo = apptNo;
    }

    public String getApptParty() {
        return apptParty;
    }

    public void setApptParty(String apptParty) {
        this.apptParty = apptParty;
    }

    public String getMatter() {
        return matter;
    }

    public void setMatter(String matter) {
        this.matter = matter;
    }

    public LocalDate getExpectDate() {
        return expectDate;
    }

    public void setExpectDate(LocalDate expectDate) {
        this.expectDate = expectDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTransferApplyId() {
        return transferApplyId;
    }

    public void setTransferApplyId(Long transferApplyId) {
        this.transferApplyId = transferApplyId;
    }

    public Long getDeliveryApplyId() {
        return deliveryApplyId;
    }

    public void setDeliveryApplyId(Long deliveryApplyId) {
        this.deliveryApplyId = deliveryApplyId;
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
