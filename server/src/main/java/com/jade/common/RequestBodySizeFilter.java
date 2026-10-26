package com.jade.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求体闸门，拦在解析之前。
 *
 * <p>已有的两道都不在最前面：{@code spring.servlet.multipart} 的 8MB/10MB 只管 multipart 表单，
 * 管不到 JSON；Jackson 的 20M 字符硬上限要把整包读完才撞线——一个 40MB 的 base64 请求会先把
 * 堆和线程时间吃掉，再回 400。这里只看 Content-Length，超限一个字节都不读直接回 413。
 *
 * <p>上限取 10MB，与 multipart 的 max-request-size 同档：完工照片在浏览器侧已压到长边 1280、
 * JPEG 0.82（{@code external.html}），单张 base64 约 0.2–0.5MB，留了二十倍余量。
 *
 * <p>分块传输（无 Content-Length）不走这里，仍由 Jackson 的字符上限兜底。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodySizeFilter extends OncePerRequestFilter {

    private static final long LIMIT_BYTES = 10L * 1024 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        if (req.getContentLengthLong() <= LIMIT_BYTES) {
            chain.doFilter(req, resp);
            return;
        }
        // 直接回包不会走到 SecurityHeadersFilter（它在更内层），基础响应头在这里自己补齐一份
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Referrer-Policy", "no-referrer");
        resp.setHeader("Cache-Control", "no-store");
        resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":413,\"message\":\"请求体超过 10MB 上限\"}");
    }
}
