package com.k12english.web;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.k12english.web.dto.ApiError;

/** 统一异常处理：任何异常都回一个结构化的 JSON，而不是 Spring 默认的 HTML 错误页。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return json(HttpStatus.BAD_REQUEST, "bad_request", e.getMessage());
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MissingServletRequestParameterException.class })
    public ResponseEntity<ApiError> unreadable(Exception e) {
        // 请求体反序列化失败时，客户端只看到一句笼统提示，真正的原因（哪个字段不匹配）
        // 必须留在服务端日志里，否则排查全靠猜。
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        log.warn("请求体无法解析: {}", root.getMessage());
        return json(HttpStatus.BAD_REQUEST, "bad_request", "请求体格式不正确");
    }

    /**
     * Spring 自带的 HTTP 语义异常：找不到资源、请求方法不支持、媒体类型不支持。
     *
     * <p>这类异常必须单独接住，否则会被下面的 {@link Exception} 兜底吞成 500。这不是
     * 理论问题：浏览器每次打开页面都会请求 {@code /favicon.ico}，SPA 升级后旧标签页
     * 也会去取已经不存在的 {@code index-<旧哈希>.js} —— 它们本该是 404，若变成 500，
     * 日志会被无意义的堆栈刷满，前端也拿不到可读的错误码。
     *
     * <p>注意不能改成 {@code @ExceptionHandler(ErrorResponse.class)}：Spring 选择处理器时
     * 用的 {@code ExceptionDepthComparator} 只沿 {@code getSuperclass()} 走，不遍历接口，
     * 接口类型的匹配深度会被算成 {@code Integer.MAX_VALUE}，反而输给 {@code Exception}。
     */
    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class })
    public ResponseEntity<ApiError> httpSemantics(Exception e) {
        // 参数类型只能是 Exception（ErrorResponse 不是 Throwable，拿不到 getMessage）。
        HttpStatusCode status = (e instanceof ErrorResponse er)
                ? er.getStatusCode()
                : HttpStatus.INTERNAL_SERVER_ERROR;
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String code = (resolved != null) ? resolved.name().toLowerCase(Locale.ROOT) : "http_error";
        log.debug("HTTP {} {}: {}", status.value(), code, e.getMessage());
        return json(status, code, describe(status.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("接口异常", e);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    /** 客户端错误用中文说明，避免把 Spring 的英文原始消息直接透给界面。 */
    private static String describe(int status) {
        return switch (status) {
            case 404 -> "接口或资源不存在";
            case 405 -> "请求方法不被支持";
            case 406 -> "无法返回客户端可接受的格式";
            case 415 -> "请求的媒体类型不被支持";
            default -> "请求无法处理";
        };
    }

    /**
     * 统一出口。
     *
     * <p>必须显式声明 {@code application/json}：静态资源处理器会把响应的 Content-Type
     * 预设成 {@code text/javascript}、{@code text/css} 之类，若不在 ResponseEntity 上覆盖，
     * 消息转换器会因为找不到匹配 preset Content-Type 的 converter 而抛
     * {@code HttpMessageNotWritableException} —— 结果是异常处理器自己失败，
     * 客户端拿到一个没有结构的错误页，日志里还多一条误导性的堆栈。
     */
    private static ResponseEntity<ApiError> json(HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(code, message));
    }
}
