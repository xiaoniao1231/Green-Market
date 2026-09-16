package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web03.pojo.Result;
import org.web03.utils.CurrentHolder;


@Slf4j
@RestController
public class LogoutController {

    /**
     * 退出登录。
     *
     * <p>鉴权用的是无状态 JWT：服务端不保存会话，令牌是否失效由前端清除本地登录态决定。
     * 这里只需要一个「成功」响应，让前端退出流程能干净收尾（此前该路径没有映射，
     * 前端 POST /logout 会拿到 404，虽然被 catch 掉不影响退出，但日志里全是无效请求）。
     */
    @PostMapping("/logout")
    public Result logout() {
        log.info("用户退出登录：{}", CurrentHolder.getCurrentUserId());
        return Result.success(null);
    }
}
