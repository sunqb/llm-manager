package com.llmmanager.agent.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息转换器
 * 负责在自定义 Message 和 Spring AI Message 之间进行转换
 *
 * 支持多模态消息（图片、文件等）
 */
@Slf4j
public class MessageConverter {

    /**
     * 将自定义 Message 转换为 Spring AI Message
     */
    public static org.springframework.ai.chat.messages.Message toSpringAiMessage(Message message) {
        if (message == null) {
            return null;
        }

        return switch (message.getMessageType()) {
            case SYSTEM -> new org.springframework.ai.chat.messages.SystemMessage(message.getContent());
            case USER -> {
                // 多模态消息支持
                if (message instanceof MediaMessage mediaMessage && mediaMessage.hasMedia()) {
                    yield convertMediaMessage(mediaMessage);
                } else {
                    yield new org.springframework.ai.chat.messages.UserMessage(message.getContent());
                }
            }
            case ASSISTANT -> new org.springframework.ai.chat.messages.AssistantMessage(message.getContent());
            case TOOL -> {
                // 处理工具消息
                if (message instanceof ToolMessage toolMessage) {
                    yield convertToolMessage(toolMessage);
                } else {
                    throw new IllegalArgumentException("TOOL 类型消息必须是 ToolMessage 实例");
                }
            }
            default -> throw new IllegalArgumentException("不支持的消息类型: " + message.getMessageType());
        };
    }

    /**
     * 转换多模态消息为 Spring AI UserMessage
     */
    private static org.springframework.ai.chat.messages.UserMessage convertMediaMessage(MediaMessage mediaMessage) {
        List<Media> mediaList = new ArrayList<>();

        for (MediaMessage.MediaContent content : mediaMessage.getMediaContents()) {
            Media media = convertToSpringAiMedia(content);
            if (media != null) {
                mediaList.add(media);
            }
        }

        if (mediaList.isEmpty()) {
            // 没有有效的媒体内容，返回纯文本消息
            return new org.springframework.ai.chat.messages.UserMessage(mediaMessage.getContent());
        }

        // 使用 Builder 创建带媒体内容的 UserMessage（Spring AI 1.1.0+ API）
        return org.springframework.ai.chat.messages.UserMessage.builder()
                .text(mediaMessage.getContent())
                .media(mediaList)
                .build();
    }

    /**
     * 将 MediaContent 转换为 Spring AI Media
     */
    private static Media convertToSpringAiMedia(MediaMessage.MediaContent content) {
        try {
            MimeType mimeType = parseMimeType(content);

            if (content.isUrlMode()) {
                // URL 模式：使用构造器
                return new Media(mimeType, URI.create(content.getMediaUrl()));
            } else if (content.isDataMode()) {
                // 数据模式（Base64 或原始字节）：使用 Builder（Spring AI 1.1.0+ API）
                return Media.builder()
                        .mimeType(mimeType)
                        .data(content.getMediaData())
                        .build();
            } else {
                log.warn("[MessageConverter] 媒体内容无效，既没有 URL 也没有 Data");
                return null;
            }
        } catch (Exception e) {
            log.error("[MessageConverter] 转换媒体内容失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析 MIME 类型
     */
    private static MimeType parseMimeType(MediaMessage.MediaContent content) {
        // 优先使用显式指定的 mimeType
        if (content.getMimeType() != null && !content.getMimeType().isEmpty()) {
            return MimeType.valueOf(content.getMimeType());
        }

        // 根据 MediaType 推断默认 MIME 类型
        return switch (content.getMediaType()) {
            case IMAGE -> MimeTypeUtils.IMAGE_PNG;  // 默认 PNG
            case DOCUMENT -> MimeType.valueOf("application/pdf");  // 默认 PDF
            case AUDIO -> MimeType.valueOf("audio/mpeg");  // 默认 MP3
            case VIDEO -> MimeType.valueOf("video/mp4");  // 默认 MP4
            default -> MimeTypeUtils.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * 转换工具消息
     */
    private static org.springframework.ai.chat.messages.Message convertToolMessage(ToolMessage toolMessage) {
        // Spring AI 支持 ToolResponseMessage（如果可用）
        // 否则降级为 AssistantMessage
        return new org.springframework.ai.chat.messages.AssistantMessage(
                String.format("[工具调用结果] %s: %s",
                        toolMessage.getToolName(),
                        toolMessage.getToolResult())
        );
    }

    /**
     * 将 Spring AI Message 转换为自定义 Message
     */
    public static Message fromSpringAiMessage(org.springframework.ai.chat.messages.Message springAiMessage) {
        if (springAiMessage == null) {
            return null;
        }

        // 根据消息类型提取内容
        String content;
        if (springAiMessage instanceof org.springframework.ai.chat.messages.SystemMessage systemMsg) {
            content = systemMsg.getText();
            return SystemMessage.of(content);
        } else if (springAiMessage instanceof org.springframework.ai.chat.messages.UserMessage userMsg) {
            content = userMsg.getText();
            // TODO: 如果需要，可以解析 UserMessage 中的 Media 并转换为 MediaMessage
            return UserMessage.of(content);
        } else if (springAiMessage instanceof org.springframework.ai.chat.messages.AssistantMessage assistantMsg) {
            content = assistantMsg.getText();
            return AssistantMessage.of(content);
        } else {
            throw new IllegalArgumentException("不支持的 Spring AI 消息类型: " + springAiMessage.getClass());
        }
    }

    /**
     * 批量转换为 Spring AI Message 列表
     */
    public static List<org.springframework.ai.chat.messages.Message> toSpringAiMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        return messages.stream()
                .map(MessageConverter::toSpringAiMessage)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换为自定义 Message 列表
     */
    public static List<Message> fromSpringAiMessages(List<org.springframework.ai.chat.messages.Message> springAiMessages) {
        if (springAiMessages == null || springAiMessages.isEmpty()) {
            return new ArrayList<>();
        }
        return springAiMessages.stream()
                .map(MessageConverter::fromSpringAiMessage)
                .collect(Collectors.toList());
    }
}
