package com.k12english.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 异常处理契约测试。
 *
 * <p>这里保护的是一个真实踩过的坑：Spring 自带的 HTTP 语义异常（找不到静态资源、
 * 方法不支持、媒体类型不支持）一旦被 {@code Exception} 兜底吞掉就会变成 500。
 * 浏览器每次打开页面都会请求 {@code /favicon.ico}，所以这个坑会持续往日志里刷堆栈。
 *
 * <p>用 standalone MockMvc 而不是 {@code @SpringBootTest}：这里只验证 advice 自身的行为，
 * 不需要起数据库、词库和整个容器。
 */
class ApiExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("找不到静态资源：404，而不是 500")
    void missingResourceIsNotFound() throws Exception {
        mvc.perform(get("/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("非法参数：400")
    void illegalArgumentIsBadRequest() throws Exception {
        mvc.perform(get("/illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    @DisplayName("未预期异常：500 且带结构化错误体")
    void unexpectedIsInternalError() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("internal_error"));
    }

    /** 只用来抛异常，不承载业务。 */
    @RestController
    static class ProbeController {

        @GetMapping("/missing-resource")
        void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "missing-resource", "missing-resource");
        }

        @GetMapping("/illegal")
        void illegal() {
            throw new IllegalArgumentException("参数不对");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("炸了");
        }
    }
}
