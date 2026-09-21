package org.web03.utils;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web03.exception.BusinessException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云OSS操作工具类
*/

@Component
public class AliyunOSSOperator {

    @Autowired
    private AliyunOSSProperties aliyunOSSProperties;

    /**
     * 取得访问凭证：优先用 application.yml 的 aliyun.oss.accessKeyId/accessKeySecret
     * （通常写成 ${OSS_ACCESS_KEY_ID:} 从环境变量注入），否则回退到环境变量。
     *
     * <p>两者都缺失时抛出可读的业务异常 —— 否则 SDK 只会抛
     * "Access key id should not be null or empty."，排查时看不出该配什么。
     */
    private CredentialsProvider resolveCredentials() throws Exception {
        String ak = aliyunOSSProperties.getAccessKeyId();
        String sk = aliyunOSSProperties.getAccessKeySecret();
        if (StringUtils.hasLength(ak) && StringUtils.hasLength(sk)) {
            return new DefaultCredentialProvider(ak, sk);
        }
        String envAk = System.getenv("OSS_ACCESS_KEY_ID");
        String envSk = System.getenv("OSS_ACCESS_KEY_SECRET");
        if (!StringUtils.hasLength(envAk) || !StringUtils.hasLength(envSk)) {
            throw new BusinessException("OSS 未配置访问凭证：请设置环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET，"
                    + "或在 application.yml 的 aliyun.oss.accessKeyId / accessKeySecret 中配置");
        }
        return CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
    }

    public String upload(byte[] content, String originalFilename) throws Exception {

        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();
        String region = aliyunOSSProperties.getRegion();

        CredentialsProvider credentialsProvider = resolveCredentials();

        // 填写Object完整路径，例如202406/1.png。Object完整路径中不能包含Bucket名称。
        //获取当前系统日期的字符串,格式为 yyyy/MM
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        //生成一个新的不重复的文件名
        String newFileName = UUID.randomUUID() + originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = dir + "/" + newFileName;

        // 创建OSSClient实例。
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        OSS ossClient = OSSClientBuilder.create()
                .endpoint(endpoint)
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(region)
                .build();

        try {
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(content));
        } finally {
            ossClient.shutdown();
        }

        return endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + objectName;
    }

}
