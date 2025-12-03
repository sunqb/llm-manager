package com.llmmanager.agent.storage.core.service;

import com.llmmanager.agent.storage.core.entity.MediaFile;

import java.util.List;

/**
 * 媒体文件 Service 接口
 *
 * 职责：封装 MediaFile 的 CRUD 操作
 * 用途：保存多模态对话中的图片、文件等媒体信息
 *
 * 命名规范：
 * - fileCode：文件业务唯一标识
 * - conversationCode：会话业务唯一标识
 * - messageCode：消息业务唯一标识
 */
public interface MediaFileService {

    /**
     * 保存单个媒体文件记录
     *
     * @param mediaFile 媒体文件实体
     */
    void save(MediaFile mediaFile);

    /**
     * 批量保存媒体文件记录
     *
     * @param mediaFiles 媒体文件列表
     */
    void saveBatch(List<MediaFile> mediaFiles);

    /**
     * 根据消息标识查询关联的媒体文件
     *
     * @param messageCode 消息标识
     * @return 媒体文件列表
     */
    List<MediaFile> findByMessageCode(String messageCode);

    /**
     * 根据会话标识查询所有媒体文件
     *
     * @param conversationCode 会话标识
     * @return 媒体文件列表
     */
    List<MediaFile> findByConversationCode(String conversationCode);

    /**
     * 根据文件标识查询
     *
     * @param fileCode 文件标识
     * @return 媒体文件
     */
    MediaFile findByFileCode(String fileCode);

    /**
     * 软删除指定会话的所有媒体文件
     *
     * @param conversationCode 会话标识
     */
    void deleteByConversationCode(String conversationCode);

    /**
     * 软删除指定消息的所有媒体文件
     *
     * @param messageCode 消息标识
     */
    void deleteByMessageCode(String messageCode);

    /**
     * 统计指定消息关联的媒体文件数量
     *
     * @param messageCode 消息标识
     * @return 媒体文件数量
     */
    int countByMessageCode(String messageCode);

    /**
     * 保存图片URL记录（便捷方法）
     *
     * @param conversationCode 会话标识
     * @param messageCode      消息标识
     * @param imageUrl         图片URL
     * @param mimeType         MIME类型（可为null，默认image/png）
     * @return 创建的媒体文件实体
     */
    MediaFile saveImageUrl(String conversationCode, String messageCode, String imageUrl, String mimeType);

    /**
     * 批量保存图片URL记录（便捷方法）
     *
     * @param conversationCode 会话标识
     * @param messageCode      消息标识
     * @param imageUrls        图片URL列表
     * @return 创建的媒体文件列表
     */
    List<MediaFile> saveImageUrls(String conversationCode, String messageCode, List<String> imageUrls);

    /**
     * 为最新的用户消息保存图片URL（便捷方法）
     * 自动查找最新的用户消息并关联
     *
     * @param conversationCode 会话标识
     * @param imageUrls        图片URL列表
     * @return 创建的媒体文件列表，如果找不到用户消息则返回空列表
     */
    List<MediaFile> saveImageUrlsForLatestUserMessage(String conversationCode, List<String> imageUrls);
}
