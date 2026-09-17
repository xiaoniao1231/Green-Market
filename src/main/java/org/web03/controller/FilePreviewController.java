package org.web03.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web03.utils.AliyunOSSProperties;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 文件内联预览代理：把 OSS 上的文档以「浏览器可以直接内嵌显示」的方式回吐给前端。
 *
 * <p>为什么需要它：
 * <ol>
 *   <li>OSS 上的对象响应头带 {@code Content-Disposition: attachment}，浏览器用 iframe 直接打开会变成下载；</li>
 *   <li>Bucket 未配置跨域（CORS）时，前端也无法用 fetch 自行取内容。</li>
 * </ol>
 * 走同源代理后，PDF / 纯文本 / Office 文档都能在聊天页的预览窗口里直接看，不用先下载。
 *
 * <p>安全：只允许代理本项目 Bucket 下的对象（按 Bucket 域名白名单校验），
 * 避免这个接口被当成任意 URL 代理（SSRF）。对象本身是公共读的，代理不额外扩大暴露面。
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FilePreviewController {

    @Autowired
    private AliyunOSSProperties ossProperties;

    /**
     * 内联预览：{@code GET /files/preview?url=<OSS 地址>}
     *
     * @param probe 前端用来探测本接口是否已实现（带上它只回 200，不读取文件）
     */
    @GetMapping("/preview")
    public void preview(@RequestParam(value = "url", required = false) String url,
                        @RequestParam(value = "probe", required = false) String probe,
                        HttpServletResponse response) throws Exception {
        /* 探测请求：接口存在即返回 200，前端据此决定是否走代理（未实现时它会拿到 404 并自动降级） */
        if (probe != null) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        if (!StringUtils.hasLength(url) || !isAllowed(url)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            log.warn("预览源文件读取失败 status={} url={}", status, url);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            return;
        }

        String contentType = conn.getContentType();
        if (!StringUtils.hasLength(contentType)) {
            contentType = "application/octet-stream";
        }
        /* 文本类补上 charset，否则窗口里的中文会乱码 */
        if (contentType.startsWith("text/") && !contentType.toLowerCase().contains("charset")) {
            contentType = contentType + "; charset=UTF-8";
        }
        response.setContentType(contentType);
        /* 关键：覆盖 OSS 对象自带的 attachment，让浏览器内联渲染而不是下载 */
        response.setHeader("Content-Disposition", "inline");
        response.setHeader("Cache-Control", "private, max-age=600");

        try (InputStream in = conn.getInputStream()) {
            in.transferTo(response.getOutputStream());
        }
    }

    /** 只放行本项目 Bucket 的对象：https://&lt;bucket&gt;.&lt;endpoint 主机&gt;/... */
    private boolean isAllowed(String url) {
        String endpoint = ossProperties.getEndpoint();
        String bucket = ossProperties.getBucketName();
        if (!StringUtils.hasLength(endpoint) || !StringUtils.hasLength(bucket)) {
            return false;
        }
        String host = endpoint.replaceFirst("^https?://", "").replaceAll("/+$", "");
        return url.startsWith("https://" + bucket + "." + host + "/")
                || url.startsWith("http://" + bucket + "." + host + "/");
    }
}
