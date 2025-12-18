package com.llmmanager.ops.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * Sa-Token 配置属性
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "sa-token.auth")
public class SaTokenProperties {

    /**
     * 免登录路径列表
     */
    private List<String> excludePaths = Arrays.asList(
            "/api/auth/login",
            "/api/external/**",
            "/error",
            "/actuator/**"
    );

    @PostConstruct
    public void init() {
        log.info("[SaToken] 免登录路径配置: {}", excludePaths);
    }
}
