package com.gdu.zeus.ops.workorder.client;

import com.gdu.zeus.ops.workorder.services.CustomerSupportAssistant;
import com.gdu.zeus.ops.workorder.util.TokenContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


@RequestMapping("/api/assistant")
@RestController
public class AssistantController {

    private final CustomerSupportAssistant agent;

    public AssistantController(CustomerSupportAssistant agent) {
        this.agent = agent;
    }

    @CrossOrigin
    @RequestMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam(name = "chatId") String chatId,
                             @RequestParam(name = "userMessage") String userMessage) {
        return agent.chat(chatId, userMessage);
    }

    @CrossOrigin
    @RequestMapping(path = "/chatByUserId", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam(name = "userId") String userId,
            @RequestHeader(name = "Authorization", required = false) String token,
            @RequestParam(name = "chatId") String chatId,
            @RequestParam(name = "userMessage") String userMessage) {
        try {
            // 将token存入ThreadLocal
            if (token != null) {
                TokenContext.setToken(token);
            }
            return agent.chat(userId, chatId, userMessage)
                    .doFinally(signalType -> TokenContext.clear()); // 请求结束清理
        } catch (Exception e) {
            TokenContext.clear();
            throw e;
        }
    }

    /**
     * 停止流
     */
    @CrossOrigin
    @PostMapping("/stop")
    public ResponseEntity<String> stopChat(@RequestParam(name = "chatId") String chatId) {
        boolean stopped = agent.stopChat(chatId);
        if (stopped) {
            return ResponseEntity.ok("Chat stream stopped successfully");
        } else {
            return ResponseEntity.ok("No active chat stream found for chatId: " + chatId);
        }
    }

}
