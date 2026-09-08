package org.web03.exception;

/**
 * 业务异常：业务校验不通过时抛出，由全局异常处理器统一转换为 Result(code=0)
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
