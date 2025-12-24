package com.llmmanager.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.config.WebSearchToolsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 联网搜索工具（默认适配 SearxNG JSON API）
 */
@Slf4j
@Component
public class WebSearchTools {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private WebSearchToolsProperties properties;

    @Tool(description = "联网搜索（需要配置自建 SearxNG：llm.tools.web-search.base-url + enabled=true），返回标题/链接/摘要。")
    public SearchResult searchWeb(
            @ToolParam(description = "搜索关键词") String query,
            @ToolParam(description = "返回条数，默认 5，最大 10") int limit) {

        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return SearchResult.failed("联网搜索工具未启用，请设置 llm.tools.web-search.enabled=true");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            return SearchResult.failed("未配置搜索服务，请设置 llm.tools.web-search.base-url（推荐自建 SearxNG）");
        }
        if (!StringUtils.hasText(query)) {
            return SearchResult.failed("query 不能为空");
        }

        int effectiveLimit = limit > 0 ? limit : (properties.getDefaultLimit() != null ? properties.getDefaultLimit() : 5);
        effectiveLimit = Math.min(effectiveLimit, 10);

        int timeoutSeconds = properties.getTimeoutSeconds() != null ? properties.getTimeoutSeconds() : 20;
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 20;
        }

        String baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String language = StringUtils.hasText(properties.getLanguage()) ? properties.getLanguage().trim() : "zh-CN";
        int safeSearch = properties.getSafeSearch() != null ? properties.getSafeSearch() : 1;

        String url = baseUrl + "/search"
                + "?q=" + encodedQuery
                + "&format=json"
                + "&language=" + URLEncoder.encode(language, StandardCharsets.UTF_8)
                + "&safesearch=" + safeSearch;

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return SearchResult.failed("搜索服务响应失败，status=" + response.statusCode());
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode resultsNode = root.path("results");
            if (!resultsNode.isArray()) {
                return SearchResult.failed("搜索服务返回格式不符合预期（缺少 results）");
            }

            List<SearchItem> items = new ArrayList<>();
            for (JsonNode node : resultsNode) {
                if (items.size() >= effectiveLimit) {
                    break;
                }
                String title = textOrNull(node.get("title"));
                String resultUrl = textOrNull(node.get("url"));
                String snippet = textOrNull(node.get("content"));

                if (!StringUtils.hasText(title) && !StringUtils.hasText(resultUrl)) {
                    continue;
                }
                items.add(new SearchItem(title, resultUrl, snippet));
            }

            return new SearchResult(true, new SearchData(query, items.size(), items), null);
        } catch (Exception e) {
            log.warn("[WebSearchTools] searchWeb 失败: {}", e.getMessage());
            return SearchResult.failed("搜索失败: " + e.getMessage());
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    public record SearchResult(
            boolean success,
            SearchData data,
            String error
    ) {
        public static SearchResult failed(String error) {
            return new SearchResult(false, null, error);
        }
    }

    public record SearchData(
            String query,
            int count,
            List<SearchItem> results
    ) {}

    public record SearchItem(
            String title,
            String url,
            String snippet
    ) {}
}
