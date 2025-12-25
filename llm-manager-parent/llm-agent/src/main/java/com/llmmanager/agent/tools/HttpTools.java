package com.llmmanager.agent.tools;

import com.llmmanager.agent.config.HttpToolsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 工具（安全版）
 */
@Slf4j
@Component
public class HttpTools {

    private static final int DEFAULT_MAX_CHARS = 100_000;
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE);

    @Resource
    private HttpToolsProperties httpToolsProperties;

    @Tool(description = "通过 HTTP GET 抓取网页/接口的文本内容（安全限制：仅 http/https、域名白名单、默认禁止私网）。")
    public FetchResult fetchUrl(
            @ToolParam(description = "URL（仅支持 http/https）") String url,
            @ToolParam(description = "最大返回字符数，默认 100000") int maxChars,
            @ToolParam(description = "超时秒数，默认按配置 llm.tools.http.default-timeout-seconds") int timeoutSeconds) {

        if (!Boolean.TRUE.equals(httpToolsProperties.getEnabled())) {
            return FetchResult.failed("HTTP 工具未启用，请设置 llm.tools.http.enabled=true");
        }
        if (!StringUtils.hasText(url)) {
            return FetchResult.failed("URL 不能为空");
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return FetchResult.failed("URL 格式错误: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return FetchResult.failed("仅支持 http/https URL");
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            return FetchResult.failed("URL 缺少 host");
        }

        String normalizedHost = normalizeHost(host);
        if (!isHostAllowed(normalizedHost, httpToolsProperties.getAllowHosts())) {
            return FetchResult.failed("目标域名不在白名单: " + normalizedHost);
        }

        if (!Boolean.TRUE.equals(httpToolsProperties.getAllowPrivateNetwork())) {
            String privateCheckError = validateNotPrivateAddress(normalizedHost);
            if (privateCheckError != null) {
                return FetchResult.failed(privateCheckError);
            }
        }

        int effectiveMaxChars = maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
        int maxResponseBytes = Optional.ofNullable(httpToolsProperties.getMaxResponseBytes()).orElse(1024 * 1024);
        if (maxResponseBytes <= 0) {
            maxResponseBytes = 1024 * 1024;
        }
        int readLimitBytes = Math.min(maxResponseBytes, Math.max(1024, effectiveMaxChars * 4));

        int effectiveTimeoutSeconds = timeoutSeconds > 0
                ? timeoutSeconds
                : Optional.ofNullable(httpToolsProperties.getDefaultTimeoutSeconds()).orElse(20);
        if (effectiveTimeoutSeconds <= 0) {
            effectiveTimeoutSeconds = 20;
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(effectiveTimeoutSeconds))
                .build();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(effectiveTimeoutSeconds))
                .header("User-Agent", Optional.ofNullable(httpToolsProperties.getUserAgent()).orElse("LLM-Manager/1.0"))
                .header("Accept", "text/plain,text/html,application/json,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = response.statusCode();
            String finalUrl = response.uri() != null ? response.uri().toString() : uri.toString();
            String contentType = response.headers().firstValue("content-type").orElse(null);

            if (!isTextLikeContentType(contentType)) {
                return new FetchResult(false, statusCode, finalUrl, contentType, null, false,
                        "响应不是文本类型，content-type=" + (contentType != null ? contentType : "unknown"));
            }

            ReadBytesResult read = readUpTo(response.body(), readLimitBytes);
            Charset charset = resolveCharset(contentType);
            String content = new String(read.bytes, charset);
            boolean truncated = read.truncated;

            if (content.length() > effectiveMaxChars) {
                content = content.substring(0, effectiveMaxChars);
                truncated = true;
            }

            return new FetchResult(true, statusCode, finalUrl, contentType, content, truncated, null);
        } catch (Exception e) {
            log.warn("[HttpTools] fetchUrl 失败: {}", e.getMessage());
            return FetchResult.failed("请求失败: " + e.getMessage());
        }
    }

    private static String normalizeHost(String host) {
        String lower = host.trim().toLowerCase(Locale.ROOT);
        try {
            return IDN.toASCII(lower);
        } catch (Exception e) {
            return lower;
        }
    }

    private static boolean isHostAllowed(String host, List<String> allowHosts) {
        if (!StringUtils.hasText(host) || allowHosts == null || allowHosts.isEmpty()) {
            return false;
        }

        String hostLower = host.toLowerCase(Locale.ROOT);
        for (String raw : allowHosts) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String pattern = raw.trim().toLowerCase(Locale.ROOT);
            if ("*".equals(pattern)) {
                return true;
            }
            if (pattern.startsWith("*.")) {
                String suffix = pattern.substring(1); // ".example.com"
                if (hostLower.endsWith(suffix) && hostLower.length() > suffix.length()) {
                    return true;
                }
                continue;
            }
            if (hostLower.equals(pattern)) {
                return true;
            }
        }

        return false;
    }

    private static String validateNotPrivateAddress(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address != null && isPrivateAddress(address)) {
                    return "禁止访问私网/本机地址: " + host + " -> " + address.getHostAddress();
                }
            }
            return null;
        } catch (Exception e) {
            return "域名解析失败: " + e.getMessage();
        }
    }

    private static boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes != null && bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 100 && second >= 64 && second <= 127) {
                return true; // 100.64.0.0/10
            }
        }

        if (address instanceof Inet6Address && bytes != null && bytes.length == 16) {
            int first = bytes[0] & 0xFF;
            if ((first & 0xFE) == 0xFC) {
                return true; // fc00::/7
            }
        }

        return false;
    }

    private static boolean isTextLikeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("text/")
                || ct.contains("json")
                || ct.contains("xml")
                || ct.contains("yaml")
                || ct.contains("markdown");
    }

    private static Charset resolveCharset(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return StandardCharsets.UTF_8;
        }
        Matcher m = CHARSET_PATTERN.matcher(contentType);
        if (!m.find()) {
            return StandardCharsets.UTF_8;
        }
        String charset = m.group(1);
        if (!StringUtils.hasText(charset)) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charset.trim());
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private static ReadBytesResult readUpTo(InputStream inputStream, int maxBytes) throws Exception {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            boolean truncated = false;
            while (true) {
                int read = in.read(buffer);
                if (read == -1) {
                    break;
                }
                int remaining = maxBytes - total;
                if (remaining <= 0) {
                    truncated = true;
                    break;
                }
                int toWrite = Math.min(read, remaining);
                out.write(buffer, 0, toWrite);
                total += toWrite;
                if (toWrite < read) {
                    truncated = true;
                    break;
                }
            }
            return new ReadBytesResult(out.toByteArray(), truncated);
        }
    }

    private record ReadBytesResult(byte[] bytes, boolean truncated) {}

    public record FetchResult(
            boolean success,
            int statusCode,
            String finalUrl,
            String contentType,
            String content,
            boolean truncated,
            String error
    ) {
        public static FetchResult failed(String error) {
            return new FetchResult(false, 0, null, null, null, false, error);
        }
    }
}

