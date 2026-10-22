package com.k12english.web;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.k12english.config.UserContext;
import com.k12english.service.AuthService;
import com.k12english.service.AuthService.SessionUser;

/**
 * 登录闸门。
 *
 * <p>放行规则：
 * <ul>
 *   <li><b>非 {@code /api/} 请求一律放行</b> —— 前端单页、样式、脚本、发音音频都要能加载，
 *       否则连登录界面都显示不出来。这些资源里没有个人数据。</li>
 *   <li>{@code /api/auth/login}、{@code /api/auth/register} 放行（没登录态才需要调它们）</li>
 *   <li>{@code /api/auth/me}、{@code /api/auth/logout} 放行，由控制器自己判断有没有登录</li>
 *   <li>其余 {@code /api/**} 必须有有效会话，否则 401 JSON</li>
 *   <li>{@code /api/admin/**} 还要求 role = admin，否则 403</li>
 * </ul>
 *
 * <p>通过后把用户 id 写进 {@link UserContext}，业务仓储据此过滤数据。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService auth;

    public AuthFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        UserContext.clear();
        try {
            String path = req.getRequestURI();

            // 静态资源：放行，但上下文保持匿名
            if (!path.startsWith("/api/")) {
                chain.doFilter(req, res);
                return;
            }

            Optional<SessionUser> user = auth.resolve(readCookie(req));
            user.ifPresent(u -> UserContext.set(u.id()));

            // 账号相关接口自己判断登录态（me 要返回 401、logout 要能重复调用），一律放行
            if (path.startsWith("/api/auth/")) {
                chain.doFilter(req, res);
                return;
            }

            if (user.isEmpty()) {
                writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, "{\"error\":\"未登录\"}");
                return;
            }

            if (path.startsWith("/api/admin/") && !user.get().admin()) {
                writeJson(res, HttpServletResponse.SC_FORBIDDEN, "{\"error\":\"需要管理员权限\"}");
                return;
            }

            chain.doFilter(req, res);
        } finally {
            UserContext.clear();
        }
    }

    private String readCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (AuthService.COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void writeJson(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        res.getWriter().write(body);
        res.getWriter().flush();
    }
}
