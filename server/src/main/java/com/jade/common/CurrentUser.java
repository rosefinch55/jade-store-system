package com.jade.common;

import java.util.Collections;
import java.util.List;

/**
 * 当前登录用户上下文。由 AuthInterceptor 写入，Controller 与 Service 按需读取。
 */
public class CurrentUser {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private final Long userId;
    private final String username;
    private final List<String> roles;
    private final List<String> perms;

    public CurrentUser(Long userId, String username, List<String> roles, List<String> perms) {
        this.userId = userId;
        this.username = username;
        this.roles = roles == null ? Collections.emptyList() : roles;
        this.perms = perms == null ? Collections.emptyList() : perms;
    }

    public static void set(CurrentUser u) {
        HOLDER.set(u);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 取当前用户，未登录时抛 401 */
    public static CurrentUser require() {
        CurrentUser u = HOLDER.get();
        if (u == null) {
            throw BizException.unauthorized("未登录");
        }
        return u;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPerms() {
        return perms;
    }

    public boolean hasRole(String code) {
        return roles.contains(code);
    }

    public boolean isBoss() {
        return roles.contains("BOSS");
    }

    public boolean hasPerm(String uc) {
        return perms.contains(uc);
    }
}
