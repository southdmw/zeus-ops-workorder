package com.gdu.zeus.ops.workorder.data;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "chat_session")
public class ChatSession implements Serializable {
    
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
     * 聊天会话ID (UUID)
     */
    @Column(nullable = false, unique = true)
    private String chatId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 是否已删除
     */
    @Column(nullable = false)
    private Boolean deleted = false;
}
