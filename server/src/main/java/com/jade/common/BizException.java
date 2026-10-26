package com.jade.common;

/**
 * 业务异常。code 直接映射为 HTTP 状态码：400/401/403/404/409/422/423/429。
 */
public class BizException extends RuntimeException {
    private final int code;
    private final Object data;

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    public static BizException badRequest(String msg) {
        return new BizException(400, msg);
    }

    public static BizException unauthorized(String msg) {
        return new BizException(401, msg);
    }

    public static BizException forbidden(String msg) {
        return new BizException(403, msg);
    }

    public static BizException notFound(String msg) {
        return new BizException(404, msg);
    }

    public static BizException conflict(String msg) {
        return new BizException(409, msg);
    }

    public static BizException conflict(String msg, Object data) {
        return new BizException(409, msg, data);
    }

    public static BizException unprocessable(String msg) {
        return new BizException(422, msg);
    }
}
