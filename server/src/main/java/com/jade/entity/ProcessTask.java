package com.jade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 加工任务（UC-16） */
@TableName("t_process_task")
public class ProcessTask {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号 */
    private String taskNo;

    /** 货品 -> t_goods（加工对象） */
    private Long goodsId;

    /** 加工师傅 -> t_source_party（类别=加工师傅，分派对象） */
    private Long craftsmanId;

    /** 加工要求 */
    private String requirement;

    /** 工费 */
    private BigDecimal laborFee;

    /** 期限 */
    private LocalDate deadline;

    /** 状态：待加工|在加工|待交付|已交付 */
    private String status;

    /** 门店催办次数。「逾期」本身是派生态（待加工/在加工且过期限），只有催办动作落库 */
    private Integer urgeCount;

    /** 最近一次催办时间 */
    private LocalDateTime lastUrgeAt;

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

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public Long getCraftsmanId() {
        return craftsmanId;
    }

    public void setCraftsmanId(Long craftsmanId) {
        this.craftsmanId = craftsmanId;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public BigDecimal getLaborFee() {
        return laborFee;
    }

    public void setLaborFee(BigDecimal laborFee) {
        this.laborFee = laborFee;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getUrgeCount() {
        return urgeCount;
    }

    public void setUrgeCount(Integer urgeCount) {
        this.urgeCount = urgeCount;
    }

    public LocalDateTime getLastUrgeAt() {
        return lastUrgeAt;
    }

    public void setLastUrgeAt(LocalDateTime lastUrgeAt) {
        this.lastUrgeAt = lastUrgeAt;
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
