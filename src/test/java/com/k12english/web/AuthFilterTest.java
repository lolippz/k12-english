package com.k12english.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.k12english.config.UserContext;
import com.k12english.repo.UserRepository;
import com.k12english.service.AuthService;
import com.k12english.service.AuthService.AuthResult;
import com.k12english.support.TestDatabase;

/**
 * 登录闸门的放行规则测试。
 *
 * <p>重点回归：{@code /api/words} 必须放行——前端 main.tsx 在渲染登录页之前
 * 就要拉词库，拦了会导致未登录时启动直接 401（2026-10-26 实际发生过）。
 */
class AuthFilterTest {

    private AuthService auth;
    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        auth = new AuthService(new UserRepository(TestDatabase.freshJdbc()));
        filter = new AuthFilter(auth);
    }

    private MockHttpServletResponse run(String path, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        if (token != null) {
            req.setCookies(new jakarta.servlet.http.Cookie(AuthService.COOKIE, token));
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return res;
    }

    private boolean passed(String path, String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        if (token != null) {
            req.setCookies(new jakarta.servlet.http.Cookie(AuthService.COOKIE, token));
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }

    @Test
    void 词库对未登录放行() throws Exception {
        assertNull(run("/api/words", null).getErrorMessage());
        assertEquals(true, passed("/api/words", null));
    }

    @Test
    void 个人数据未登录返回401() throws Exception {
        MockHttpServletResponse res = run("/api/state", null);
        assertEquals(401, res.getStatus());
        assertEquals(true, res.getContentAsString().contains("未登录"));
    }

    @Test
    void 管理接口未登录返回401() throws Exception {
        assertEquals(401, run("/api/admin/users", null).getStatus());
    }

    @Test
    void 静态资源与auth接口放行() throws Exception {
        assertEquals(true, passed("/index.html", null));
        assertEquals(true, passed("/assets/index-abc.js", null));
        assertEquals(true, passed("/api/auth/login", null));
        assertEquals(true, passed("/api/auth/status", null));
    }

    @Test
    void 管理员会话可进管理接口且写入用户上下文() throws Exception {
        AuthResult r = auth.register("校长", "admin123", null, "127.0.0.1");
        assertEquals(true, r.ok(), "首个注册用户应创建成功");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/users");
        req.setCookies(new jakarta.servlet.http.Cookie(AuthService.COOKIE, r.token()));
        MockHttpServletResponse res = new MockHttpServletResponse();
        // AuthFilter 在 finally 里清理 ThreadLocal（防串号），所以要在链路下游捕获
        java.util.concurrent.atomic.AtomicReference<String> inChain = new java.util.concurrent.atomic.AtomicReference<>();
        filter.doFilter(req, res, (request, response) -> inChain.set(UserContext.current()));

        assertNotNull(inChain.get(), "管理员应放行且下游能读到用户 id");
        assertEquals(r.user().id(), inChain.get(), "应把用户 id 写入上下文");
    }

    @Test
    void 普通用户访问管理接口返回403() throws Exception {
        AuthResult admin = auth.register("校长", "admin123", null, "127.0.0.1");
        var invite = auth.createInvite(1, "", "测试", admin.user().id());
        AuthResult user = auth.register("小明", "stu1234", invite.code(), "127.0.0.1");
        assertEquals(true, user.ok());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/users");
        req.setCookies(new jakarta.servlet.http.Cookie(AuthService.COOKIE, user.token()));
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(403, res.getStatus());
    }
}
