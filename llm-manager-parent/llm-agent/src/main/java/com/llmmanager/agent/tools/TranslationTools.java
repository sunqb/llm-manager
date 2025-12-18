package com.llmmanager.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 翻译工具 - Mock 数据
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class TranslationTools {

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "zh", "中文",
            "en", "英语",
            "ja", "日语",
            "ko", "韩语",
            "fr", "法语",
            "de", "德语",
            "es", "西班牙语"
    );

    // 模拟翻译词典（实际应调用翻译 API）
    private static final Map<String, Map<String, String>> TRANSLATIONS = Map.of(
            "你好", Map.of("en", "Hello", "ja", "こんにちは", "ko", "안녕하세요", "fr", "Bonjour", "de", "Hallo"),
            "谢谢", Map.of("en", "Thank you", "ja", "ありがとう", "ko", "감사합니다", "fr", "Merci", "de", "Danke"),
            "再见", Map.of("en", "Goodbye", "ja", "さようなら", "ko", "안녕히 가세요", "fr", "Au revoir", "de", "Auf Wiedersehen"),
            "Hello", Map.of("zh", "你好", "ja", "こんにちは", "ko", "안녕하세요", "fr", "Bonjour", "de", "Hallo"),
            "Thank you", Map.of("zh", "谢谢", "ja", "ありがとう", "ko", "감사합니다", "fr", "Merci", "de", "Danke"),
            "Goodbye", Map.of("zh", "再见", "ja", "さようなら", "ko", "안녕히 가세요", "fr", "Au revoir", "de", "Auf Wiedersehen")
    );

    @Tool(description = "将文本翻译成指定语言")
    public TranslationResult translate(
            @ToolParam(description = "要翻译的文本") String text,
            @ToolParam(description = "目标语言代码：zh(中文)、en(英语)、ja(日语)、ko(韩语)、fr(法语)、de(德语)") String targetLanguage) {

        log.info("[TranslationTools] 翻译文本: '{}' -> {}", text, targetLanguage);

        String langName = LANGUAGE_NAMES.getOrDefault(targetLanguage.toLowerCase(), targetLanguage);

        // 尝试从词典查找
        Map<String, String> translations = TRANSLATIONS.get(text);
        if (translations != null && translations.containsKey(targetLanguage.toLowerCase())) {
            String translated = translations.get(targetLanguage.toLowerCase());
            return new TranslationResult(true, text, translated, langName, null);
        }

        // 模拟翻译（实际应调用 API）
        String mockTranslation = generateMockTranslation(text, targetLanguage);
        return new TranslationResult(true, text, mockTranslation, langName, null);
    }

    @Tool(description = "检测文本的语言")
    public LanguageDetectionResult detectLanguage(
            @ToolParam(description = "要检测语言的文本") String text) {

        log.info("[TranslationTools] 检测语言: '{}'", text);

        String detectedCode;
        double confidence;

        // 简单的语言检测逻辑
        if (text.matches(".*[\\u4e00-\\u9fa5]+.*")) {
            detectedCode = "zh";
            confidence = 0.95;
        } else if (text.matches(".*[\\u3040-\\u309F\\u30A0-\\u30FF]+.*")) {
            detectedCode = "ja";
            confidence = 0.92;
        } else if (text.matches(".*[\\uAC00-\\uD7AF]+.*")) {
            detectedCode = "ko";
            confidence = 0.93;
        } else if (text.matches(".*[a-zA-Z]+.*")) {
            detectedCode = "en";
            confidence = 0.88;
        } else {
            detectedCode = "unknown";
            confidence = 0.5;
        }

        String langName = LANGUAGE_NAMES.getOrDefault(detectedCode, "未知");
        return new LanguageDetectionResult(true, detectedCode, langName, confidence, null);
    }

    private String generateMockTranslation(String text, String targetLanguage) {
        return switch (targetLanguage.toLowerCase()) {
            case "en" -> "[Mock EN] " + text;
            case "zh" -> "[模拟翻译] " + text;
            case "ja" -> "[モック翻訳] " + text;
            case "ko" -> "[모의 번역] " + text;
            case "fr" -> "[Traduction simulée] " + text;
            case "de" -> "[Mock-Übersetzung] " + text;
            default -> "[Translated] " + text;
        };
    }

    // ==================== 数据结构 ====================

    public record TranslationResult(
            boolean success,
            String originalText,
            String translatedText,
            String targetLanguage,
            String error
    ) {}

    public record LanguageDetectionResult(
            boolean success,
            String languageCode,
            String languageName,
            double confidence,
            String error
    ) {}
}

