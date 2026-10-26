package com.jade.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 操作日志（管理端留痕）：记录所有登录后的改操作，公开匿名接口不记 */
@TableName("t_op_log")
public class OpLog {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 -> t_user，匿名时为空 */
    private Long userId;

    /** 操作时账号快照 */
    private String username;

    /** HTTP 方法 */
    private String method;

    /** 请求路径（含目标 id） */
    private String path;

    /** 查询串 */
    private String query;

    /** HTTP 状态码（4xx/5xx 即失败的操作也留痕） */
    private Integer status;

    /** 操作时间 */
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
