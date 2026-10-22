package com.k12english.web;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.k12english.config.AccessToken;

/**
 * 访问口令闸门。
 *
 * <p>本项目原本是纯单机应用，没有任何账号体系。一旦用隧道挂到公网，
 * 拿到链接的人就能直接调用 API 改写学习记录。这个过滤器在「非本机直连」时
 * 要求先输入一次口令，之后靠 Cookie 记住 30 天。
 *
 * <p>放行规则：
 * <ol>
 *   <li>口令校验被关闭（{@code app.access-token=off}）—— 全放行</li>
 *   <li>本机直连（remoteAddr 是回环 <b>且</b> 没有代理头）—— 全放行，日常使用无感</li>
 *   <li>带有效 Cookie / {@code X-Access-Token} 头 —— 放行</li>
 *   <li>URL 带 {@code ?k=口令} —— 发 Cookie 后 302 去掉 {@code k}，避免口令留在地址栏和 Referer 里</li>
 *   <li>其余 —— 返回登录页</li>
 * </ol>
 *
 * <p>第 2 条为什么要检查代理头：cloudflared / nginx 都跑在本机，它们连 8080 时
 * remoteAddr 也是 127.0.0.1。若只看回环，公网流量会被误判成本机而直接放行。
 * 这些代理在转发时一定会补 {@code X-Forwarded-For} 之类的头，用它来区分。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessGuardFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "k12_access";
    private static final String AUTH_PATH = "/__auth";
    private static final long COOKIE_MAX_AGE_SECONDS = Duration.ofDays(30).toSeconds();

    private final AccessToken token;

    public AccessGuardFilter(AccessToken token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (!token.isEnabled()) {
            chain.doFilter(req, res);
            return;
        }

        if (AUTH_PATH.equals(req.getRequestURI())) {
            handleLogin(req, res);
            return;
        }

        if (isDirectLocal(req)
                || hasValidCookie(req)
                || token.matches(req.getHeader("X-Access-Token"))) {
            chain.doFilter(req, res);
            return;
        }

        // 只对 GET 读 ?k=，避免影响 POST 的 JSON 请求体
        if ("GET".equalsIgnoreCase(req.getMethod())) {
            String k = req.getParameter("k");
            if (k != null) {
                if (token.matches(k)) {
                    issueCookie(req, res);
                    res.setStatus(HttpServletResponse.SC_FOUND);
                    res.setHeader(HttpHeaders.LOCATION, withoutKey(req));
                } else {
                    // 链接里的口令不对，直接把错误显示出来，别让人对着空白页猜
                    renderLogin(req, res, true);
                }
                return;
            }
        }

        renderLogin(req, res, false);
    }

    /* ---------------- 判定 ---------------- */

    private boolean isDirectLocal(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        boolean loopback = "127.0.0.1".equals(remote)
                || "::1".equals(remote)
                || "0:0:0:0:0:0:0:1".equals(remote);
        if (!loopback) {
            return false;
        }
        return req.getHeader("X-Forwarded-For") == null
                && req.getHeader("CF-Connecting-IP") == null
                && req.getHeader("X-Real-IP") == null
                && req.getHeader("Forwarded") == null;
    }

    private boolean hasValidCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && token.matches(c.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isHttps(HttpServletRequest req) {
        return req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto"));
    }

    /* ---------------- 登录 ---------------- */

    private void handleLogin(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!"POST".equalsIgnoreCase(req.getMethod())) {
            renderLogin(req, res, false);
            return;
        }
        String submitted = req.getParameter("k");
        if (submitted != null && token.matches(submitted)) {
            issueCookie(req, res);
            res.setStatus(HttpServletResponse.SC_FOUND);
            res.setHeader(HttpHeaders.LOCATION, "/");
            return;
        }
        renderLogin(req, res, true);
    }

    private void issueCookie(HttpServletRequest req, HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token.value())
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofSeconds(COOKIE_MAX_AGE_SECONDS))
                .sameSite("Lax")
                .secure(isHttps(req))
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 去掉查询串里的 k 参数，其余原样保留。 */
    private String withoutKey(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String qs = req.getQueryString();
        if (qs == null || qs.isEmpty()) {
            return uri;
        }
        StringBuilder keep = new StringBuilder();
        for (String part : qs.split("&")) {
            if (part.isEmpty() || part.equals("k") || part.startsWith("k=")) {
                continue;
            }
            if (keep.length() > 0) {
                keep.append('&');
            }
            keep.append(part);
        }
        return keep.length() == 0 ? uri : uri + "?" + keep;
    }

    /* ---------------- 登录页 ---------------- */

    private void renderLogin(HttpServletRequest req, HttpServletResponse res, boolean failed) throws IOException {
        // 接口请求返回 401 更符合语义（便于排查）；页面请求用 200，浏览器才不会弹原生登录框
        boolean api = req.getRequestURI().startsWith("/api/");
        res.setStatus(api ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_OK);
        res.setContentType("text/html;charset=UTF-8");
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        res.setHeader("X-Robots-Tag", "noindex, nofollow");
        res.getWriter().write(LOGIN_PAGE.replace("{{ERROR}}", failed ? ERROR_BLOCK : ""));
        res.getWriter().flush();
    }

    private static final String ERROR_BLOCK = """
            <p class="err">口令不对，再试一次</p>
            """;

    private static final String LOGIN_PAGE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
            <meta name="theme-color" content="#ffffff" />
            <meta name="robots" content="noindex, nofollow" />
            <title>需要访问口令 · 英语学习</title>
            <style>
              :root {
                --bg: #ffffff; --surface: #f7f8fa; --border: #e3e6ea; --border-strong: #cbd2d9;
                --text: #1a1d21; --text-3: #7a828a; --primary: #185fa5; --bad: #a32d2d;
              }
              * { box-sizing: border-box; }
              html, body { height: 100%; margin: 0; }
              body {
                display: flex; align-items: center; justify-content: center;
                padding: 24px 20px calc(24px + env(safe-area-inset-bottom));
                background: var(--surface); color: var(--text);
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                font-size: 15px; line-height: 1.6;
                -webkit-text-size-adjust: 100%; -webkit-font-smoothing: antialiased;
              }
              .box {
                width: 100%; max-width: 380px; background: var(--bg);
                border: 1px solid var(--border); border-radius: 14px; padding: 30px 24px;
                box-shadow: 0 1px 2px rgba(16,24,40,.04), 0 8px 24px rgba(16,24,40,.06);
              }
              h1 { font-size: 19px; font-weight: 600; margin: 0 0 6px; letter-spacing: -.01em; }
              .sub { color: var(--text-3); font-size: 13.5px; margin: 0 0 20px; }
              label { display: block; font-size: 13.5px; font-weight: 500; margin-bottom: 8px; }
              input {
                width: 100%; border: 1px solid var(--border-strong); border-radius: 10px;
                padding: 13px 15px; font-size: 17px; letter-spacing: 1px;
                font-family: inherit; background: var(--bg); color: var(--text); outline: none;
              }
              input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(24,95,165,.12); }
              button {
                width: 100%; margin-top: 16px; border: 1px solid var(--primary); background: var(--primary);
                color: #fff; border-radius: 10px; padding: 14px; font-size: 16px;
                font-family: inherit; cursor: pointer; min-height: 48px;
              }
              button:active { background: #0c447c; }
              .err { color: var(--bad); font-size: 13.5px; margin: 0 0 14px; }
              .hint { color: var(--text-3); font-size: 12.5px; margin: 18px 0 0; line-height: 1.7; }
              code { font-family: "SFMono-Regular", Consolas, monospace; font-size: 12px; }
              @media (max-width: 420px) {
                body { padding: 16px 14px; align-items: flex-start; padding-top: 12vh; }
                .box { padding: 24px 18px; }
              }
            </style>
            </head>
            <body>
              <form class="box" method="post" action="/__auth">
                <h1>需要访问口令</h1>
                <p class="sub">这个学习站只在私人范围内分享，输入口令后 30 天内不用再输。</p>
                {{ERROR}}
                <label for="k">访问口令</label>
                <input id="k" name="k" type="password" autocomplete="off" autocapitalize="off"
                       autocorrect="off" spellcheck="false" required autofocus />
                <button type="submit">进入</button>
                <p class="hint">
                  口令在运行服务的电脑上：启动日志里有一行「访问口令」，
                  也保存在 <code>~/.k12-english/access.token</code>。
                </p>
              </form>
            </body>
            </html>
            """;
}
