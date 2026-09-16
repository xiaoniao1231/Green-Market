package org.web03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ServletComponentScan//启用Servlet组件扫描
@EnableScheduling //启用定时任务：ChatWebSocketHandler.sweepDeadSessions() 心跳超时回收依赖它，缺失时僵死连接永不清理
@SpringBootApplication
public class Web03Application {

    public static void main(String[] args) {
        SpringApplication.run(Web03Application.class, args);
    }

}
