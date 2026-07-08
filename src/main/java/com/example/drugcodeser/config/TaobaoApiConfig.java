
package com.example.drugcodeser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "taobao.api")
public class TaobaoApiConfig {

    private String serverUrl;
    private String appKey;
    private String appSecret;
    private String format = "json";
    private String signMethod = "md5";
    private int connectTimeout = 30000;
    private int readTimeout = 60000;
    private String refEntId;
    private String authRefUserId;
    private String fromRefUserId;
    private String toRefUserId;
    private String agentRefEntId;
    private String license;
    private int licenseTokenCacheSeconds = 86400;
}
