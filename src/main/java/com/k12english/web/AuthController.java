package com.k12english.web;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.k12english.repo.UserRepository;
import com.k12english.service.AuthService;
import com.k12english.service.AuthService.AuthResult;
import com.k12english.service.AuthService.SessionUser;

/** 注册 / 登录 / 登出 / 当前用户。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UserRepository users;

    public AuthController(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    public record LoginRequest(String username, String password) {
    }

    public record RegisterRequest(String username, String password, String invite) {
    }

    /**
     * 登录页初始化用：库里有没有账号。
     * 没有账号时前端直接显示「注册管理员」而不是登录表单 —— 新装的应用没人能登录。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        long n = users.countUsers();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasUsers", n > 0);
        m.put("userCount", n);
        return m;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req,
            HttpServletRequest http, HttpServletResponse res) {
        AuthResult r = auth.register(req.username(), req.password(), req.invite(), clientIp(http));
        return respond(r, http, res);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req,
            HttpServletRequest http, HttpServletResponse res) {
        AuthResult r = auth.login(req.username(), req.password(), clientIp(http));
        return respond(r, http, res);
    }

    /** 当前登录用户。未登录返回 401，前端据此显示登录页。 */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @CookieValue(name = AuthService.COOKIE, required = false) String token) {
        return auth.resolve(token)
                .map(u -> ResponseEntity.ok(userJson(u)))
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "未登录")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @CookieValue(name = AuthService.COOKIE, required = false) String token,
            HttpServletRequest http, HttpServletResponse res) {
        auth.logout(token);
        ResponseCookie cleared = ResponseCookie.from(AuthService.COOKIE, "")
                .httpOnly(true).path("/").maxAge(Duration.ZERO)
                .sameSite("Lax").secure(isHttps(http)).build();
        res.addHeader(HttpHeaders.SET_COOKIE, cleared.toString());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /* ---------------------------------------------------------------- 内部 */

    private ResponseEntity<Map<String, Object>> respond(AuthResult r,
            HttpServletRequest http, HttpServletResponse res) {
        if (!r.ok()) {
            return ResponseEntity.badRequest().body(Map.of("error", r.error()));
        }
        ResponseCookie cookie = ResponseCookie.from(AuthService.COOKIE, r.token())
                .httpOnly(true).path("/").maxAge(Duration.ofDays(7))
                .sameSite("Lax").secure(isHttps(http)).build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(userJson(r.user()));
    }

    private static Map<String, Object> userJson(SessionUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("username", u.username());
        m.put("role", u.role());
        m.put("admin", u.admin());
        return m;
    }

    private static boolean isHttps(HttpServletRequest req) {
        return req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto"));
    }

    /**
     * 取真实来源 IP。经过 cloudflared / 反代时 {@code getRemoteAddr()} 全是 127.0.0.1，
     * 拿它做限流会把所有公网用户算成同一个来源，一人输错就把别人一起锁了。
     */
    static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String cf = req.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        return req.getRemoteAddr();
    }
}
