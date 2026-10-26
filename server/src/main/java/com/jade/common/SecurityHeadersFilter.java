package com.jade.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 安全响应头。浏览器侧的基础防线，和接口鉴权是两回事：
 * - nosniff：禁止浏览器猜 MIME，防把上传内容当脚本执行
 * - DENY：禁止被 iframe 嵌套，防点击劫持
 * - no-referrer：跳转外部时不带来源地址
 * - 接口响应 no-store：令牌与业务数据不落代理与浏览器磁盘缓存
 * - 静态库与图片长缓存：隧道链路慢，二次访问必须走本地缓存；页面 HTML 用 no-cache 保证改版即生效
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Referrer-Policy", "no-referrer");
        String uri = req.getRequestURI();
        if (uri.startsWith("/api/")) {
            resp.setHeader("Cache-Control", "no-store");
            resp.setHeader("Pragma", "no-cache");
        } else if (uri.startsWith("/lib/") || uri.startsWith("/images/") || uri.startsWith("/uploads/")
                || uri.equals("/qr-share.png") || uri.equals("/qr-external.png") || uri.equals("/logo.png")
                || uri.equals("/favicon.ico")) {
            resp.setHeader("Cache-Control", "public, max-age=604800");
        } else if (uri.endsWith(".html") || uri.equals("/")) {
            resp.setHeader("Cache-Control", "no-cache");
        }
        chain.doFilter(req, resp);
    }
}
