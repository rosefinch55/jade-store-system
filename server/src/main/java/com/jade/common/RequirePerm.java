package com.jade.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限点，值为用例编号（如 UC-12）。拦截器比对登录用户的权限集合。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {
    /** 需要其中一个权限即可 */
    String[] value();

    /** 需要其中一个角色即可，与 value 同时满足 */
    String[] roles() default {};
}
