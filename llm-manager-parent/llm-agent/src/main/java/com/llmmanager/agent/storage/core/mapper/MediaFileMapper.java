package com.llmmanager.agent.storage.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.llmmanager.agent.storage.core.entity.MediaFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 媒体文件 Mapper
 *
 * 命名规范：
 * - fileCode：文件业务唯一标识
 * - conversationCode：会话业务唯一标识
 * - messageCode：消息业务唯一标识
 */
@Mapper
public interface MediaFileMapper extends BaseMapper<MediaFile> {

    /**
     * 根据会话标识查询媒体文件
     */
    @Select("SELECT * FROM a_media_files WHERE conversation_code = #{conversationCode} AND is_delete = 0 ORDER BY create_time ASC")
    List<MediaFile> selectByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 根据消息标识查询媒体文件
     */
    @Select("SELECT * FROM a_media_files WHERE message_code = #{messageCode} AND is_delete = 0 ORDER BY create_time ASC")
    List<MediaFile> selectByMessageCode(@Param("messageCode") String messageCode);

    /**
     * 根据文件标识查询
     */
    @Select("SELECT * FROM a_media_files WHERE file_code = #{fileCode} AND is_delete = 0")
    MediaFile selectByFileCode(@Param("fileCode") String fileCode);

    /**
     * 软删除指定会话的所有媒体文件
     */
    @Update("UPDATE a_media_files SET is_delete = 1 WHERE conversation_code = #{conversationCode}")
    int softDeleteByConversationCode(@Param("conversationCode") String conversationCode);

    /**
     * 软删除指定消息的所有媒体文件
     */
    @Update("UPDATE a_media_files SET is_delete = 1 WHERE message_code = #{messageCode}")
    int softDeleteByMessageCode(@Param("messageCode") String messageCode);

    /**
     * 统计指定消息的媒体文件数量
     */
    @Select("SELECT COUNT(*) FROM a_media_files WHERE message_code = #{messageCode} AND is_delete = 0")
    int countByMessageCode(@Param("messageCode") String messageCode);
}
