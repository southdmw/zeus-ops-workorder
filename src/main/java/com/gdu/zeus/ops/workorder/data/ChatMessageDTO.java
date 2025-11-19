package com.gdu.zeus.ops.workorder.data;

import com.gdu.zeus.ops.workorder.data.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
    private Long id;
    private String userId;
    private String chatId;
    private MessageRole role;
    private String content;
    private LocalDateTime createdAt;
}
