package com.jade.common;

import com.jade.entity.OpLog;
import com.jade.mapper.OpLogMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 鉴权拦截器。解析 Authorization 头，写入 CurrentUser，并按 @RequirePerm 校验。
 * 另在 afterCompletion 落操作日志：登录后的所有改操作（POST/PUT/DELETE）都留痕，公开匿名接口不记。
 * 鉴权失败统一走 deny()：那里必须自己清 CurrentUser 并留痕，原因见 deny() 注释。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthInterceptor.class);

    private final JwtUtil jwt;
    private final OpLogMapper opLogMapper;

    public AuthInterceptor(JwtUtil jwt, OpLogMapper opLogMapper) {
        this.jwt = jwt;
        this.opLogMapper = opLogMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }
        // 严格按 RFC 6750 取令牌：必须是 "Bearer <token>"，不接受裸令牌
        String token = null;
        String header = req.getHeader("Authorization");
        if (header != null && header.length() > 7
                && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = header.substring(7).trim();
        }
        Claims claims = (token == null || token.isEmpty()) ? null : jwt.parse(token);
        if (claims != null) {
            Object uid = claims.getSubject();
            Object uname = claims.get("username");
            Object roles = claims.get("roles");
            Object perms = claims.get("perms");
            CurrentUser.set(new CurrentUser(
                    uid == null ? null : Long.valueOf(uid.toString()),
                    uname == null ? null : uname.toString(),
                    castList(roles), castList(perms)));
        }

        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        // 类级或方法级权限
        RequirePerm rp = hm.getMethodAnnotation(RequirePerm.class);
        if (rp == null) {
            rp = hm.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (rp == null) {
            // 没有权限注解不等于开放：仍然要求登录。
            // 真正免登录的路径（/api/auth/login、/api/public/**、/api/health）在 WebConfig 里已放行
            if (CurrentUser.get() == null) {
                throw deny(req, BizException.unauthorized("未登录"));
            }
            return true;
        }

        CurrentUser u = CurrentUser.get();
        if (u == null) {
            throw deny(req, BizException.unauthorized("未登录"));
        }
        if (rp.roles().length > 0) {
            boolean ok = false;
            for (String r : rp.roles()) {
                if (u.hasRole(r)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw deny(req, BizException.forbidden("当前角色无权访问"));
            }
        }
        if (rp.value().length > 0) {
            boolean ok = false;
            for (String p : rp.value()) {
                if (u.hasPerm(p)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw deny(req, BizException.forbidden("没有该功能的权限"));
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        try {
            String m = req.getMethod();
            if (("POST".equals(m) || "PUT".equals(m) || "DELETE".equals(m))
                    && req.getRequestURI().startsWith("/api/")
                    && !req.getRequestURI().equals("/api/auth/login")
                    && !req.getRequestURI().equals("/api/auth/logout")
                    && !req.getRequestURI().startsWith("/api/public/")) {
                writeLog(req, CurrentUser.get(), resp.getStatus());
            }
        } catch (Exception ignore) {
            // 留痕失败不影响业务主流程
        } finally {
            CurrentUser.clear();
        }
    }

    /**
     * 鉴权失败统一收尾：清上下文、留痕，再把原异常交给 GlobalExceptionHandler。
     *
     * <p>为什么必须在这里自己清 CurrentUser：preHandle 抛异常时，Spring 的
     * HandlerExecutionChain.interceptorIndex 还停在 -1，本拦截器的 afterCompletion
     * 不会被回调，ThreadLocal 里的身份就留在该 Tomcat 线程上了。2026-10-26 线上实测：
     * 同一条 keep-alive 连接上先让鉴权失败一次（师傅打老板接口 403，或扔个废令牌 401），
     * 紧跟着不带令牌的请求打 /api/sales 直接 200，拿到上一个账号才该看到的数据；
     * 线程复用还会把这份身份带给别的连接。
     *
     * <p>留痕口径：认得出身份的拒绝（403 越权）写进 t_op_log，管理端「操作日志」页可查；
     * 无令牌的 401 只打应用日志——那多半是扫描器，写库会被刷爆。
     */
    private BizException deny(HttpServletRequest req, BizException e) {
        CurrentUser u = CurrentUser.get();
        CurrentUser.clear();
        try {
            if (u != null) {
                writeLog(req, u, e.getCode());
            } else {
                log.warn("鉴权拒绝 {} {} -> {} {}", req.getMethod(), req.getRequestURI(),
                        e.getCode(), e.getMessage());
            }
        } catch (Exception ignore) {
            // 留痕失败不影响拒绝本身
        }
        return e;
    }

    /** 写一条操作日志（调用方负责兜异常） */
    private void writeLog(HttpServletRequest req, CurrentUser u, int status) {
        OpLog row = new OpLog();
        if (u != null) {
            row.setUserId(u.getUserId());
            row.setUsername(u.getUsername());
        }
        row.setMethod(req.getMethod());
        row.setPath(req.getRequestURI());
        String q = req.getQueryString();
        row.setQuery(q == null || q.length() <= 200 ? q : q.substring(0, 200));
        row.setStatus(status);
        opLogMapper.insert(row);
        // 留痕表无界增长会把 2G 小机拖垮：低频抽样清理，保留最近 180 天
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(500) == 0) {
            opLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OpLog>()
                    .lt("created_at", java.time.LocalDateTime.now().minusDays(180)));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object o) {
        if (o instanceof List<?> l) {
            return (List<String>) l;
        }
        return List.of();
    }
}
