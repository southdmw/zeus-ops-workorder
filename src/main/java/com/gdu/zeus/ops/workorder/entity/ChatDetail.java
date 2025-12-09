package com.gdu.zeus.ops.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话详情表实体类
 */
@Data
@Builder
@TableName("chat_detail")
public class ChatDetail {
    
    /**
     * 自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 对话ID
     */
    private String chatId;
    
    /**
     * Dify的conversationId
     */
    private String conversationId;
    
    /**
     * 对话类型：1-目标检测 2-告警研判 3-智能问数 4-创建工单
     */
    private Integer chatType;
    
    /**
     * 对话是否被结束：0-否 1-是
     */
    private Integer conversationStopFlag;
    
    /**
     * 对话结束时间
     */
    private LocalDateTime conversationStopTime;
    
    /**
     * 对话内容
     */
    private String content;

    /**
     * 图片链接
     */
    private String imgUrl;
    /**
     * 对话角色：USER-用户提问 ASSISTANT-大模型回答
     */
    private String role;
    
    /**
     * 对话内容创建时间
     */
    private LocalDateTime createTime;
}