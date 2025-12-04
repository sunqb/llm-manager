package com.llmmanager.agent.interceptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 拦截器：将 extra_body 内容展开到请求体顶层
 *
 * Spring AI 的 OpenAiChatOptions 会将 extraBody 序列化为 "extra_body" 字段，
 * 但豆包等国内模型需要参数直接在顶层（如 "thinking": {"type": "enabled"}）。
 *
 * 此拦截器会：
 * 1. 解析请求体 JSON
 * 2. 将 "extra_body" 的内容展开到顶层
 * 3. 删除原始的 "extra_body" 字段
 */
@Slf4j
public class ExtraBodyFlattenInterceptor implements ClientHttpRequestInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {
        // 只处理 chat/completions 请求
        String path = request.getURI().getPath();
        if (path != null && path.contains("chat/completions") && body != null && body.length > 0) {
            try {
                byte[] modifiedBody = flattenExtraBody(body);
                return execution.execute(request, modifiedBody);
            } catch (Exception e) {
                log.warn("[ExtraBodyFlattenInterceptor] 处理请求体失败，使用原始请求: {}", e.getMessage());
            }
        }

        return execution.execute(request, body);
    }

    /**
     * 将 extra_body 内容展开到顶层
     */
    private byte[] flattenExtraBody(byte[] originalBody) throws IOException {
        String bodyStr = new String(originalBody, StandardCharsets.UTF_8);

        // 解析为 Map
        Map<String, Object> bodyMap = objectMapper.readValue(bodyStr, new TypeReference<Map<String, Object>>() {});

        // 检查是否存在 extra_body
        Object extraBody = bodyMap.get("extra_body");
        if (extraBody instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extraBodyMap = (Map<String, Object>) extraBody;

            // 将 extra_body 的内容合并到顶层
            Map<String, Object> newBody = new HashMap<>(bodyMap);
            newBody.remove("extra_body");
            newBody.putAll(extraBodyMap);

            String newBodyStr = objectMapper.writeValueAsString(newBody);
            log.info("[ExtraBodyFlattenInterceptor] 已展开 extra_body 到顶层");
            log.info("[ExtraBodyFlattenInterceptor] 修改后的请求体:\n{}", newBodyStr);

            return newBodyStr.getBytes(StandardCharsets.UTF_8);
        }

        // 没有 extra_body，返回原始请求
        return originalBody;
    }
}
