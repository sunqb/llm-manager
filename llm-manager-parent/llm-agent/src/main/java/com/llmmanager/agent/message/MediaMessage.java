package com.llmmanager.agent.message;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模态消息
 * 支持图片、文件、音频、视频等多种媒体类型
 * 一条消息可以包含文本 + 多个媒体内容
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MediaMessage extends Message {

    /**
     * 媒体内容列表（支持多个媒体）
     */
    private List<MediaContent> mediaContents;

    /**
     * 构造函数
     */
    public MediaMessage(String textContent) {
        super(MessageType.USER, textContent);
        this.mediaContents = new ArrayList<>();
    }

    /**
     * 静态工厂方法 - 仅文本
     */
    public static MediaMessage of(String textContent) {
        return new MediaMessage(textContent);
    }

    /**
     * 静态工厂方法 - 文本 + 单个媒体
     */
    public static MediaMessage of(String textContent, MediaContent mediaContent) {
        MediaMessage message = new MediaMessage(textContent);
        message.addMediaContent(mediaContent);
        return message;
    }

    /**
     * 静态工厂方法 - 文本 + 多个媒体
     */
    public static MediaMessage of(String textContent, List<MediaContent> mediaContents) {
        MediaMessage message = new MediaMessage(textContent);
        message.setMediaContents(mediaContents);
        return message;
    }

    /**
     * 添加媒体内容
     */
    public void addMediaContent(MediaContent mediaContent) {
        if (this.mediaContents == null) {
            this.mediaContents = new ArrayList<>();
        }
        this.mediaContents.add(mediaContent);
    }

    /**
     * 是否包含媒体内容
     */
    public boolean hasMedia() {
        return mediaContents != null && !mediaContents.isEmpty();
    }

    /**
     * 媒体内容封装类
     */
    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    public static class MediaContent {
        /**
         * 媒体类型
         */
        private MediaType mediaType;

        /**
         * 媒体 URL（远程地址或本地路径）
         */
        private String mediaUrl;

        /**
         * 媒体数据（Base64 编码或原始字节）
         */
        private byte[] mediaData;

        /**
         * MIME 类型（如 image/png, application/pdf）
         */
        private String mimeType;

        /**
         * 文件名（可选）
         */
        private String fileName;

        /**
         * 文件大小（字节）
         */
        private Long fileSize;

        /**
         * 构造函数
         */
        public MediaContent(MediaType mediaType, String mediaUrl) {
            this.mediaType = mediaType;
            this.mediaUrl = mediaUrl;
        }

        public MediaContent(MediaType mediaType, byte[] mediaData, String mimeType) {
            this.mediaType = mediaType;
            this.mediaData = mediaData;
            this.mimeType = mimeType;
        }

        /**
         * 静态工厂方法 - URL
         */
        public static MediaContent ofUrl(MediaType mediaType, String mediaUrl) {
            return new MediaContent(mediaType, mediaUrl);
        }

        /**
         * 静态工厂方法 - 图片 URL
         */
        public static MediaContent ofImageUrl(String imageUrl) {
            return new MediaContent(MediaType.IMAGE, imageUrl);
        }

        /**
         * 静态工厂方法 - 数据
         */
        public static MediaContent ofData(MediaType mediaType, byte[] data, String mimeType) {
            return new MediaContent(mediaType, data, mimeType);
        }

        /**
         * 静态工厂方法 - 图片数据
         */
        public static MediaContent ofImageData(byte[] imageData, String mimeType) {
            return new MediaContent(MediaType.IMAGE, imageData, mimeType);
        }

        /**
         * 是否是 URL 模式
         */
        public boolean isUrlMode() {
            return mediaUrl != null && !mediaUrl.isEmpty();
        }

        /**
         * 是否是数据模式
         */
        public boolean isDataMode() {
            return mediaData != null && mediaData.length > 0;
        }
    }
}
