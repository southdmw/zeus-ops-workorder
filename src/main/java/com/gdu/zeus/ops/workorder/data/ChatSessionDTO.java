package com.gdu.zeus.ops.workorder.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天会话DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionDTO {
    private Long id;
    private String userId;
    private String chatId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
