package com.gdu.zeus.ops.workorder.client;

import com.gdu.zeus.ops.workorder.dto.*;
import com.gdu.zeus.ops.workorder.entity.ChatDetail;
import com.gdu.zeus.ops.workorder.services.ChatService;
import com.gdu.zeus.ops.workorder.services.CustomerSupportAssistant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话页面
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/v2")
@RequiredArgsConstructor
public class ChatController {
    
    private final ChatService chatService;
    private final CustomerSupportAssistant agent;


    /**
     * 对话
     * @param request
     * @return
     */
    @CrossOrigin
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "开始聊天对话") })
    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatMessageRequest request) {
        return agent.chatWithMetadata(request);
    }

    /**
     * 获取对话列表（按时间段分组）
     */
    @GetMapping("/list")
    public Result<ChatListGroupResponse> getChatList() {
        try {
            ChatListGroupResponse chatList = chatService.getChatListGrouped("zbw3");
            return Result.success(chatList);
        } catch (Exception e) {
            log.error("获取对话列表失败", e);
            return Result.error("获取对话列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取对话详情
     * @param chatId 对话ID
     */
    @GetMapping("/detail/{chatId}")
    public Result<List<ChatDetail>> getChatDetail(@PathVariable String chatId) {
        try {
            List<ChatDetail> detail = chatService.getChatDetailAll(chatId);
            return Result.success(detail);
        } catch (Exception e) {
            log.error("获取对话详情失败", e);
            return Result.error("获取对话详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送消息（阻塞模式）
     */
//    @PostMapping("/message")
//    public Result<ChatMessageResponse> sendMessage(@RequestBody ChatMessageRequest request) {
//        try {
//            ChatMessageResponse response = chatService.sendMessage(request);
//            return Result.success(response);
//        } catch (Exception e) {
//            log.error("发送消息失败", e);
//            return Result.error("发送消息失败: " + e.getMessage());
//        }
//    }
    
    /**
     * 发送消息（流式模式）
     */
//    @PostMapping("/message/stream")
//    public SseEmitter sendMessageStream(@RequestBody ChatMessageRequest request) {
//        try {
//            return chatService.sendMessageStream(request);
//        } catch (Exception e) {
//            log.error("发送流式消息失败", e);
//            SseEmitter emitter = new SseEmitter();
//            emitter.completeWithError(e);
//            return emitter;
//        }
//    }
    
    /**
     * 停止响应
     */
    @PostMapping("/stop-response")
    public Result<Boolean> stopResponse(@RequestBody StopResponseRequest request) {
        boolean stopped = agent.stopChat(request.getConversationId());
        if (stopped) {
            return Result.success(stopped);
        } else {
            return Result.error("停止响应失败");
        }
    }
    
    /**
     * 终止流程
     */
    @PostMapping("/stop-conversation")
    public Result<Boolean> stopConversation(@RequestBody StopConversationRequest request) {
        try {
            boolean success = chatService.stopConversation(request);
            return Result.success(success);
        } catch (Exception e) {
            log.error("终止流程失败", e);
            return Result.error("终止流程失败: " + e.getMessage());
        }
    }
}
