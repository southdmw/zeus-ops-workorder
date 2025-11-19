package com.gdu.zeus.ops.workorder.repository;

import com.gdu.zeus.ops.workorder.data.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天消息仓储
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    /**
     * 分页查询聊天消息
     */
    Page<ChatMessage> findByChatIdAndDeletedFalseOrderByCreatedAtDesc(String chatId, Pageable pageable);

    /**
     * 查询聊天的所有消息
     */
    List<ChatMessage> findByChatIdAndDeletedFalseOrderByCreatedAtAsc(String chatId);

    /**
     * 根据用户和会话查询消息
     */
    List<ChatMessage> findByUserIdAndChatIdAndDeletedFalseOrderByCreatedAtDesc(String userId, String chatId);
}
