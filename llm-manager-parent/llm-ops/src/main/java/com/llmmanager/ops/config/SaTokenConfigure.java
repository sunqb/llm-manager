package com.llmmanager.ops.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
            // 跳过异步分发请求和错误页面转发
            // 这些场景下 Sa-Token 上下文可能已被清理或未初始化
            if (shouldSkipAuthentication()) {
                return;
            }

            // 放行 OPTIONS 请求 (CORS 预检)
            SaRouter.match(SaHttpMethod.OPTIONS).stop();

            // 需要登录的路径
            SaRouter.match("/**")
                    .notMatch(excludePaths)
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
    }

    /**
     * 检查当前请求是否应跳过认证
     *
     * 需要跳过的场景：
     * 1. ASYNC - 流式响应 (SSE/Flux) 过程中的错误处理
     * 2. ERROR - 错误页面转发（异常处理时 forward 到 /error）
     *
     * 这些场景下 Sa-Token 上下文可能已被清理或未初始化
     *
     * @return true 表示应跳过认证
     */
    private boolean shouldSkipAuthentication() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // RequestContext 为空时，无法获取请求信息
            // 这种情况应该跳过认证，避免 SaTokenContextException
            log.debug("[SaToken] RequestContext 为空，跳过认证");
            return true;
        }
        HttpServletRequest request = attrs.getRequest();
        DispatcherType dispatcherType = request.getDispatcherType();

        // 跳过异步分发和错误页面转发
        if (dispatcherType == DispatcherType.ASYNC || dispatcherType == DispatcherType.ERROR) {
            log.debug("[SaToken] 跳过 {} 类型请求的认证", dispatcherType);
            return true;
        }
        return false;
    }
}
