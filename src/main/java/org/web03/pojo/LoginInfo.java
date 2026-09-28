package org.web03.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 登录信息
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginInfo {
    private Integer id;
    private String username; //用户名
    private String name; //姓名
    private String token; //令牌
    private String shopId; //店铺ID
    /* 资料字段：登录响应一并带回，前端 commitLogin 会把它们写进本地登录态。
       缺了这几个字段，重新登录后本地登录态里就没有性别 / 头像 / 签名，
       账户设置页只能回落到默认值（曾表现为「改成男，重新登录又变保密」）。 */
    private String gender; //性别：male 男 / female 女 / secret 保密
    private String avatar; //头像（阿里云 OSS 地址；历史数据为 emoji 字符）
    private String signature; //个性签名
}
