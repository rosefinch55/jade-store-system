package com.jade.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。把异常映射为契约里的错误码，前端按 code 判断。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Object>> handleBiz(BizException e) {
        HttpStatus status = HttpStatus.resolve(e.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage(), e.getData()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest().body(Result.fail(400, msg.isEmpty() ? "参数不合法" : msg));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Object>> handleParam(Exception e) {
        return ResponseEntity.badRequest().body(Result.fail(400, "参数缺失或格式错误"));
    }

    /**
     * 请求体读不出来：JSON 语法错、字段类型不匹配、或 body 超过 Jackson 的
     * 20M 字符上限（base64 上传塞超大图就是这条）。都是客户端的错，
     * 不该被下面的兜底报成 500「服务端异常」——线上实测原来就是这样。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Object>> handleUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Result.fail(400, "请求体格式不正确或过大"));
    }

    @ExceptionHandler(java.time.format.DateTimeParseException.class)
    public ResponseEntity<Result<Object>> handleDate(java.time.format.DateTimeParseException e) {
        return ResponseEntity.badRequest().body(Result.fail(400, "日期或时间格式不正确"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Object>> handleIllegal(IllegalArgumentException e) {
        // 原 message 不回显：JDK / MyBatis 抛的 IAE 常带类名、字段名甚至 SQL 片段，
        // 与「500 不给内部细节」是同一条要求。真堆栈进日志。
        e.printStackTrace();
        return ResponseEntity.badRequest().body(Result.fail(400, "请求参数无法处理"));
    }

    /** 静态资源不存在（如 favicon.ico）应返回 404，不该被兜底成 500 */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Result<Object>> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(404, "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleOther(Exception e) {
        // 完整堆栈进日志，对外只给固定文案，不泄露内部实现细节
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务端异常，请稍后重试或联系门店"));
    }
}
