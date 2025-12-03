package com.llmmanager.agent.message;

/**
 * 媒体类型枚举
 * 定义支持的媒体内容类型
 */
public enum MediaType {
    /**
     * 图片（PNG, JPG, JPEG, GIF, WEBP）
     */
    IMAGE,

    /**
     * 文档（PDF, DOC, DOCX, TXT）
     */
    DOCUMENT,

    /**
     * 音频（MP3, WAV, OGG）
     */
    AUDIO,

    /**
     * 视频（MP4, AVI, MOV）
     */
    VIDEO,

    /**
     * 其他类型
     */
    OTHER
}
