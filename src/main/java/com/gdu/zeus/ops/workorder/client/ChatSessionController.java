package com.gdu.zeus.ops.workorder.client;

import com.gdu.zeus.ops.workorder.data.ChatMessageDTO;
import com.gdu.zeus.ops.workorder.data.ChatSession;
import com.gdu.zeus.ops.workorder.data.ChatSessionDTO;
import com.gdu.zeus.ops.workorder.services.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatSessionController {

    @Autowired
    private ChatSessionService sessionService;

    /**
     * 获取用户的会话列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionDTO>> getUserSessions(
            @RequestParam String userId) {
        List<ChatSessionDTO> sessions = sessionService.getUserSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 获取会话历史消息(分页)
     */
    @GetMapping("/sessions/{chatId}/messages")
    public ResponseEntity<Page<ChatMessageDTO>> getSessionMessages(
            @RequestParam String userId,
            @PathVariable String chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<ChatMessageDTO> messages = sessionService
                .getSessionMessages(userId, chatId, page, size);
        return ResponseEntity.ok(messages);
    }

    /**
     * 获取会话所有历史消息
     */
    @GetMapping("/sessions/{chatId}/messages/all")
    public ResponseEntity<List<ChatMessageDTO>> getAllSessionMessages(
            @RequestParam String userId,
            @PathVariable String chatId) {

        List<ChatMessageDTO> messages = sessionService
                .getAllSessionMessages(userId, chatId);
        return ResponseEntity.ok(messages);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<ChatSession> createSession(
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "title",required = false) String title) {
        ChatSession session = sessionService.createSession(userId, title);
        return ResponseEntity.ok(session);
    }
}
