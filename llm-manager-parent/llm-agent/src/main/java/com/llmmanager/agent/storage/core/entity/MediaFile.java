package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 媒体文件实体
 * 对应数据库表 a_media_files
 *
 * 命名规范：
 * - fileCode：文件业务唯一标识（UUID）
 * - conversationCode：会话业务唯一标识
 * - messageCode：消息业务唯一标识
 */
@Data
@TableName("a_media_files")
public class MediaFile {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件唯一标识（UUID）
     */
    private String fileCode;

    /**
     * 关联的会话标识
     */
    private String conversationCode;

    /**
     * 关联的消息标识
     */
    private String messageCode;

    /**
     * 媒体类型：IMAGE/DOCUMENT/AUDIO/VIDEO/OTHER
     */
    private String mediaType;

    /**
     * MIME类型（如 image/png, application/pdf）
     */
    private String mimeType;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 存储路径（相对路径或URL）
     */
    private String storagePath;

    /**
     * 访问URL
     */
    private String fileUrl;

    /**
     * 缩略图URL（仅图片）
     */
    private String thumbnailUrl;

    /**
     * 图片宽度（仅图片）
     */
    private Integer width;

    /**
     * 图片高度（仅图片）
     */
    private Integer height;

    /**
     * 时长（秒，仅音视频）
     */
    private Integer duration;

    /**
     * 额外元数据（JSON格式）
     */
    private String metadata;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 是否删除，0：正常，1：删除
     */
    @TableLogic
    private Integer isDelete;

    // ==================== 静态工厂方法 ====================

    /**
     * 生成文件唯一标识
     */
    public static String generateFileCode() {
        return "file-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 创建媒体文件实体
     */
    public static MediaFile create(String conversationCode, String messageCode, String mediaType, String fileName) {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFileCode(generateFileCode());
        mediaFile.setConversationCode(conversationCode);
        mediaFile.setMessageCode(messageCode);
        mediaFile.setMediaType(mediaType);
        mediaFile.setFileName(fileName);
        return mediaFile;
    }
}
