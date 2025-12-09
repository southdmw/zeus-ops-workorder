package com.gdu.zeus.ops.workorder.dto;

import lombok.Data;

import java.util.List;

/**
 * 发送消息请求DTO
 */
@Data
public class ChatMessageRequest {
    private String chatId;
    private Integer chatType;
    private String conversationId;
    private String query;
    private String userId;
    private List<FileInfoDTO> files;
    /**
     * Dify工作流的输入参数
     * 例如: {"Warning_Type": "建筑垃圾", "Image": {...}}
     */
    private LLmRecheckInputs inputs;
}