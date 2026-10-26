package com.jade.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 角色（UC-12 分配，用户-角色多对多） */
@TableName("t_role")
public class Role {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码：BOSS|SALE|MERCHANT|SUPPLIER|CRAFTSMAN */
    private String roleCode;

    /** 角色名：老板|销售|合作商户|供应商|加工师傅 */
    private String roleName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
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
}
