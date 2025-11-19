package com.gdu.zeus.ops.workorder.repository;

import com.gdu.zeus.ops.workorder.data.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 聊天会话仓储
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    /**
     * 根据用户ID和会话ID查询
     */
    Optional<ChatSession> findByUserIdAndChatIdAndDeletedFalse(String userId, String chatId);

    /**
     * 查询用户的所有会话
     */
    List<ChatSession> findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(String userId);

    /**
     * 根据会话ID查询
     */
    Optional<ChatSession> findByChatIdAndDeletedFalse(String chatId);
}
