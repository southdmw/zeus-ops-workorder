package com.gdu.zeus.ops.workorder.services;

import com.gdu.zeus.ops.workorder.data.*;
import com.gdu.zeus.ops.workorder.data.enums.MessageRole;
import com.gdu.zeus.ops.workorder.repository.ChatMessageRepository;
import com.gdu.zeus.ops.workorder.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天会话服务
 */
@Service
public class ChatSessionService {

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    /**
     * 创建新会话
     */
    public ChatSession createSession(String userId, String title) {
        String chatId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        ChatSession session = ChatSession.builder()
                .userId(userId)
                .chatId(chatId)
                .title(title != null ? title : "Chat-" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                .createdAt(now)
                .updatedAt(now)
                .deleted(false)
                .build();

        return sessionRepository.save(session);
    }

    /**
     * 获取用户的会话列表
     */
    public List<ChatSessionDTO> getUserSessions(String userId) {
        List<ChatSession> sessions = sessionRepository.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(userId);
        return sessions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取会话的分页消息
     */
    public Page<ChatMessageDTO> getSessionMessages(String userId, String chatId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = messageRepository.findByChatIdAndDeletedFalseOrderByCreatedAtDesc(chatId, pageable);
        return messages.map(this::convertToDTO);
    }

    /**
     * 获取会话的所有消息
     */
    public List<ChatMessageDTO> getAllSessionMessages(String userId, String chatId) {
        List<ChatMessage> messages = messageRepository.findByChatIdAndDeletedFalseOrderByCreatedAtAsc(chatId);
        return messages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 保存消息
     */
    public ChatMessage saveMessage(String userId, String chatId, MessageRole role, String content) {
        LocalDateTime now = LocalDateTime.now();

        ChatMessage message = ChatMessage.builder()
                .userId(userId)
                .chatId(chatId)
                .role(role)
                .content(content)
                .createdAt(now)
                .deleted(false)
                .build();

        // 同时更新会话的updatedAt
        ChatSession session = sessionRepository.findByUserIdAndChatIdAndDeletedFalse(userId, chatId)
                .orElse(null);
        if (session != null) {
            session.setUpdatedAt(now);
            sessionRepository.save(session);
        }

        return messageRepository.save(message);
    }

    /**
     * 获取聊天会话
     */
    public ChatSession getSession(String userId, String chatId) {
        return sessionRepository.findByUserIdAndChatIdAndDeletedFalse(userId, chatId)
                .orElse(null);
    }

    /**
     * 删除会话(逻辑删除)
     */
    public void deleteSession(String userId, String chatId) {
        ChatSession session = sessionRepository.findByUserIdAndChatIdAndDeletedFalse(userId, chatId)
                .orElse(null);
        if (session != null) {
            session.setDeleted(true);
            sessionRepository.save(session);
        }
    }

    private ChatSessionDTO convertToDTO(ChatSession session) {
        return ChatSessionDTO.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .chatId(session.getChatId())
                .title(session.getTitle())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ChatMessageDTO convertToDTO(ChatMessage message) {
        return ChatMessageDTO.builder()
                .id(message.getId())
                .userId(message.getUserId())
                .chatId(message.getChatId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
