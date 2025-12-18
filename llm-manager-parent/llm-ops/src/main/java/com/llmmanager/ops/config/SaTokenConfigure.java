package com.llmmanager.ops.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Slf4j
@Configuration
@EnableConfigurationProperties(SaTokenProperties.class)
public class SaTokenConfigure implements WebMvcConfigurer {

    @Resource
    private SaTokenProperties saTokenProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 从配置读取免登录路径
        String[] excludePaths = saTokenProperties.getExcludePaths().toArray(new String[0]);
        log.info("[SaToken] 注册拦截器，免登录路径: {}", saTokenProperties.getExcludePaths());

        registry.addInterceptor(new SaInterceptor(handle -> {
            // 放行 OPTIONS 请求 (CORS 预检)
            SaRouter.match(SaHttpMethod.OPTIONS).stop();

            // 需要登录的路径：鉴权 + 自定义处理
            SaRouter.match("/**")
                    .notMatch(excludePaths)
                    .check(r -> {
                        // 1. 登录校验
                        StpUtil.checkLogin();

                        // 2. 自定义处理（登录后执行）
                        String requestPath = SaHolder.getRequest().getRequestPath();
                        String method = SaHolder.getRequest().getMethod();
                        Object loginId = StpUtil.getLoginId();

                        log.info("[SaToken] {} {} 用户: {}", method, requestPath, loginId);

                        // 3. 可扩展：写缓存、记录访问日志、限流等
                        // cacheService.recordAccess(loginId, requestPath);
                    });
        })).addPathPatterns("/**");
    }
}
