package org.web03.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云OSS属性配置
 */


@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOSSProperties {
    private String endpoint;
    private String bucketName;
    private String region;
    /**
     * 访问凭证（可选）。
     * 留空时回退到环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET，
     * 便于本地用环境变量、容器用配置注入；两者都没有时上传会给出明确提示而不是抛 SDK 原始错误。
     */
    private String accessKeyId;
    private String accessKeySecret;
}