package com.gdu.zeus.ops.workorder.data;

import com.gdu.zeus.ops.workorder.data.enums.MessageRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "chat_message")
public class ChatMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID
     */
    @Column(nullable = false)
    private String userId;

    /**
     * 聊天会话ID
     */
    @Column(nullable = false)
    private String chatId;

    /**
     * 消息角色 (USER/ASSISTANT)
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageRole role;

    /**
     * 消息内容
     */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 是否已删除
     */
    @Column(nullable = false)
    private Boolean deleted = false;
}
