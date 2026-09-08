package org.web03.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.web03.pojo.Result;

/**
 * 全局异常处理器：把业务异常与数据重复异常转换为统一响应结构，
 * 避免直接返回 HTTP 500 白页，保证前端能拿到 {code:0, msg} 进行提示。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务校验异常：验证码错误、手机号已注册、参数不合法等 */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    /** 唯一键冲突：账号已存在、手机号已注册等 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("数据重复: {}", e.getMessage());
        return Result.error("账号或手机号已存在");
    }
}
