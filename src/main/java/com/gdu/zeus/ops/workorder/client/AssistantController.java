package com.gdu.zeus.ops.workorder.client;

import com.gdu.zeus.ops.workorder.services.CustomerSupportAssistant;
import com.gdu.zeus.ops.workorder.util.TokenContext;
import com.gdu.zeus.ops.workorder.util.TokenContextWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


@RequestMapping("/api/assistant")
@RestController
public class AssistantController {

    private static final Logger logger = LoggerFactory.getLogger(AssistantController.class);
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

    /**
     * 基于userId的聊天接口，支持token传播
     * 
     * 思路：
     * 1. 从Authorization header中提取token
     * 2. 将token存入ThreadLocal(TokenContext)中
     * 3. 将token注入到Reactor Context中(用于多线程场景)
     * 4. 调用agent的chat方法，大模型会在工具函数中调用业务系统API
     * 5. 在TokenInterceptor中自动从TokenContext获取token并添加到HTTP请求头
     * 6. 请求结束时清理ThreadLocal
     */
    @CrossOrigin
    @RequestMapping(path = "/chatByUserId", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam(name = "userId") String userId,
            @RequestHeader(name = "Authorization", required = false) String token,
            @RequestParam(name = "chatId") String chatId,
            @RequestParam(name = "userMessage") String userMessage) {
        
        logger.info("收到聊天请求: userId={}, chatId={}, token present={}", userId, chatId, token != null);
        
        try {
            // 将token存入ThreadLocal，在同一请求线程中使用
            if (token != null && !token.isEmpty()) {
                logger.debug("将token存入ThreadLocal上下文");
                TokenContext.setToken(token);
            } else {
                logger.warn("未提供Authorization header，后续API调用可能会失败");
            }

            // 调用agent的chat方法
            Flux<String> chatFlux = agent.chat(userId, chatId, userMessage);
            
            // 使用Reactor Context传播token(处理多线程场景)
            if (token != null && !token.isEmpty()) {
                chatFlux = TokenContextWrapper.wrapWithToken(chatFlux, token);
            }
            
            // 在Flux完成时清理ThreadLocal
            return chatFlux.doFinally(signalType -> {
                logger.debug("聊天流处理完成，清理ThreadLocal上下文");
                TokenContext.clear();
            });
            
        } catch (Exception e) {
            logger.error("聊天处理异常", e);
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
        logger.info("请求停止聊天流: chatId={}", chatId);
        boolean stopped = agent.stopChat(chatId);
        if (stopped) {
            return ResponseEntity.ok("Chat stream stopped successfully");
        } else {
            return ResponseEntity.ok("No active chat stream found for chatId: " + chatId);
        }
    }

}
