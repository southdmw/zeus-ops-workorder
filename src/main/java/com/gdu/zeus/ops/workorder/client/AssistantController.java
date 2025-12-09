package com.gdu.zeus.ops.workorder.client;

import com.gdu.zeus.ops.workorder.init.AIAlgorithmDataInitializer;
import com.gdu.zeus.ops.workorder.services.CustomerSupportAssistant;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话
 */

@Slf4j
@RequestMapping("/api/assistant")
@RestController
public class AssistantController {
    private static final Logger logger = LoggerFactory.getLogger(AIAlgorithmDataInitializer.class);
    // 定义 Context Key
    public static final String TOKEN_KEY  = "auth_token";

    private final CustomerSupportAssistant agent;

    public AssistantController(CustomerSupportAssistant agent) {
        this.agent = agent;
    }

//    @CrossOrigin
//    @RequestMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> chat(@RequestBody ChatMessageRequest request) {
//
//        return agent.chat(request);
//    }
    @CrossOrigin
    @RequestMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestHeader(name = "Authorization", required = false) String token,
            @RequestParam(name = "chatId") String chatId,
            @RequestParam(name = "userMessage") String userMessage) {
            return agent.chat(chatId, userMessage); // 请求结束清理
    }

    /**
     * 停止流
     */
    @CrossOrigin
    @PostMapping("/stop")
    public ResponseEntity<String> stopChat(@RequestParam(name = "conversationId") String chatId) {
        boolean stopped = agent.stopChat(chatId);
        if (stopped) {
            return ResponseEntity.ok("Chat stream stopped successfully");
        } else {
            return ResponseEntity.ok("No active chat stream found for chatId: " + chatId);
        }
    }

}
