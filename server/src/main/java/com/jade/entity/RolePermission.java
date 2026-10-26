package com.jade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 角色-权限（多对多拆表，一角色多权限） */
@TableName("t_role_permission")
public class RolePermission {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 -> t_role */
    private Long roleId;

    /** 权限 -> t_permission */
    private Long permissionId;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }
}
