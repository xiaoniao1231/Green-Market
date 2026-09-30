package org.web03.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    /**
     * 请求参数 / 上传部件缺失：例如 multipart 请求没带 file 字段。
     * 这类异常由 Spring 在进入 Controller 之前抛出，控制器里的 `file == null` 判断根本轮不到，
     * 因此必须在这里转成可读提示 —— 否则前端只能收到 500 白页（接口文档 6.4 要求给出原因）。
     */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public Result handleMissingParam(Exception e) {
        log.warn("请求参数缺失: {}", e.getMessage());
        String name = e instanceof MissingServletRequestPartException part
                ? part.getRequestPartName()
                : ((MissingServletRequestParameterException) e).getParameterName();
        if ("file".equals(name)) return Result.error("请上传文件");
        return Result.error("请求参数不完整：" + name);
    }

    /**
     * 上传接口收到「非 multipart 请求」：`@RequestParam("file") MultipartFile` 在请求头不是
     * multipart/form-data 时，Spring 抛的是 MultipartException（不是 Part/Parameter 缺失异常），
     * 例如售后凭证图、商品图、聊天文件接口漏传 file 时。不兜住就是 500 白页。
     */
    @ExceptionHandler(MultipartException.class)
    public Result handleMultipartException(MultipartException e) {
        log.warn("上传请求格式错误: {}", e.getMessage());
        return Result.error("请选择要上传的文件");
    }

    /** 唯一键冲突：账号已存在、手机号已注册、消息重复提交等 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKeyException(DuplicateKeyException e) {
        String raw = String.valueOf(e.getMessage());
        log.warn("数据重复: {}", raw);
        /* messages.msg_id 冲突：同一条消息被重复提交（网络重试 / 双击发送），
           与注册类冲突区分开，否则会给出「账号或手机号已存在」这种误导性提示 */
        if (raw.contains("msg_id") || raw.contains("messages")) {
            return Result.error("消息已发送，请勿重复提交");
        }
        return Result.error("账号或手机号已存在");
    }
}
