package com.llmmanager.agent.storage.core.service.impl;

import com.llmmanager.agent.storage.core.entity.ChatHistory;
import com.llmmanager.agent.storage.core.entity.MediaFile;
import com.llmmanager.agent.storage.core.mapper.MediaFileMapper;
import com.llmmanager.agent.storage.core.service.ChatHistoryService;
import com.llmmanager.agent.storage.core.service.MediaFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 媒体文件 Service 实现
 */
@Slf4j
@Service
public class MediaFileServiceImpl implements MediaFileService {

    @Resource
    private MediaFileMapper mediaFileMapper;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Override
    public void save(MediaFile mediaFile) {
        if (mediaFile == null) {
            return;
        }
        // 确保 fileCode 不为空
        if (!StringUtils.hasText(mediaFile.getFileCode())) {
            mediaFile.setFileCode(MediaFile.generateFileCode());
        }
        mediaFileMapper.insert(mediaFile);
        log.debug("[MediaFileService] 保存媒体文件: {}", mediaFile.getFileCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(List<MediaFile> mediaFiles) {
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            return;
        }
        for (MediaFile mediaFile : mediaFiles) {
            if (!StringUtils.hasText(mediaFile.getFileCode())) {
                mediaFile.setFileCode(MediaFile.generateFileCode());
            }
            mediaFileMapper.insert(mediaFile);
        }
        log.debug("[MediaFileService] 批量保存媒体文件: {} 条", mediaFiles.size());
    }

    @Override
    public List<MediaFile> findByMessageCode(String messageCode) {
        if (!StringUtils.hasText(messageCode)) {
            return Collections.emptyList();
        }
        return mediaFileMapper.selectByMessageCode(messageCode);
    }

    @Override
    public List<MediaFile> findByConversationCode(String conversationCode) {
        if (!StringUtils.hasText(conversationCode)) {
            return Collections.emptyList();
        }
        return mediaFileMapper.selectByConversationCode(conversationCode);
    }

    @Override
    public MediaFile findByFileCode(String fileCode) {
        if (!StringUtils.hasText(fileCode)) {
            return null;
        }
        return mediaFileMapper.selectByFileCode(fileCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByConversationCode(String conversationCode) {
        if (!StringUtils.hasText(conversationCode)) {
            return;
        }
        int count = mediaFileMapper.softDeleteByConversationCode(conversationCode);
        log.debug("[MediaFileService] 软删除会话媒体文件: {}, 数量: {}", conversationCode, count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByMessageCode(String messageCode) {
        if (!StringUtils.hasText(messageCode)) {
            return;
        }
        int count = mediaFileMapper.softDeleteByMessageCode(messageCode);
        log.debug("[MediaFileService] 软删除消息媒体文件: {}, 数量: {}", messageCode, count);
    }

    @Override
    public int countByMessageCode(String messageCode) {
        if (!StringUtils.hasText(messageCode)) {
            return 0;
        }
        return mediaFileMapper.countByMessageCode(messageCode);
    }

    @Override
    public MediaFile saveImageUrl(String conversationCode, String messageCode, String imageUrl, String mimeType) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }

        MediaFile mediaFile = new MediaFile();
        mediaFile.setFileCode(MediaFile.generateFileCode());
        mediaFile.setConversationCode(conversationCode);
        mediaFile.setMessageCode(messageCode);
        mediaFile.setMediaType("IMAGE");
        mediaFile.setMimeType(StringUtils.hasText(mimeType) ? mimeType : guessMimeTypeFromUrl(imageUrl));
        mediaFile.setFileUrl(imageUrl);
        mediaFile.setFileName(extractFileNameFromUrl(imageUrl));

        mediaFileMapper.insert(mediaFile);
        log.debug("[MediaFileService] 保存图片URL: {}, fileCode: {}", imageUrl, mediaFile.getFileCode());

        return mediaFile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MediaFile> saveImageUrls(String conversationCode, String messageCode, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyList();
        }

        List<MediaFile> savedFiles = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            if (StringUtils.hasText(imageUrl)) {
                MediaFile mediaFile = saveImageUrl(conversationCode, messageCode, imageUrl.trim(), null);
                if (mediaFile != null) {
                    savedFiles.add(mediaFile);
                }
            }
        }

        log.info("[MediaFileService] 批量保存图片URL: {} 条, 会话: {}, 消息: {}",
                savedFiles.size(), conversationCode, messageCode);

        return savedFiles;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MediaFile> saveImageUrlsForLatestUserMessage(String conversationCode, List<String> imageUrls) {
        if (!StringUtils.hasText(conversationCode) || imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyList();
        }

        // 查找最新的用户消息
        ChatHistory latestUserMessage = chatHistoryService.findLatestUserMessage(conversationCode);
        if (latestUserMessage == null) {
            log.warn("[MediaFileService] 未找到用户消息，无法关联媒体文件，会话: {}", conversationCode);
            return Collections.emptyList();
        }

        String messageCode = latestUserMessage.getMessageCode();
        log.info("[MediaFileService] 为最新用户消息关联图片，会话: {}, 消息: {}, 图片数: {}",
                conversationCode, messageCode, imageUrls.size());

        return saveImageUrls(conversationCode, messageCode, imageUrls);
    }

    /**
     * 从URL中猜测MIME类型
     */
    private String guessMimeTypeFromUrl(String url) {
        if (url == null) {
            return "image/png";
        }
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) {
            return "image/jpeg";
        } else if (lowerUrl.contains(".png")) {
            return "image/png";
        } else if (lowerUrl.contains(".gif")) {
            return "image/gif";
        } else if (lowerUrl.contains(".webp")) {
            return "image/webp";
        } else if (lowerUrl.contains(".svg")) {
            return "image/svg+xml";
        } else if (lowerUrl.contains(".bmp")) {
            return "image/bmp";
        } else {
            return "image/png"; // 默认
        }
    }

    /**
     * 从URL中提取文件名
     */
    private String extractFileNameFromUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            // 移除查询参数
            String path = url.split("\\?")[0];
            // 获取最后一个/后面的部分
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                return path.substring(lastSlash + 1);
            }
        } catch (Exception e) {
            log.debug("[MediaFileService] 提取文件名失败: {}", url);
        }
        return null;
    }
}
