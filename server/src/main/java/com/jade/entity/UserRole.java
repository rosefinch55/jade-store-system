package com.jade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 用户与外部账户（UC-08/UC-12），角色经 t_user_role 关联 */
@TableName("t_user_role")
public class UserRole {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 -> t_user */
    private Long userId;

    /** 角色 -> t_role */
    private Long roleId;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
