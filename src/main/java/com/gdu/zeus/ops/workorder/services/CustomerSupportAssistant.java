/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gdu.zeus.ops.workorder.services;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdu.uap.auth.core.oauth2.UAPUser;
import com.gdu.uap.auth.core.util.SecurityUtils;
import com.gdu.zeus.ops.workorder.data.enums.MessageRole;
import com.gdu.zeus.ops.workorder.dto.ChatMessageRequest;
import com.gdu.zeus.ops.workorder.entity.ChatDetail;
import com.gdu.zeus.ops.workorder.filter.TokenContext;
import com.gdu.zeus.ops.workorder.util.ToolResultHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor.TOP_K;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 自然语言交互（ChatClient）
 * 记忆能力（ChatMemory）
 * 知识检索（RAG via VectorStore）
 * 函数调用（Function Calling）
 */
@Service
public class CustomerSupportAssistant {

    private static final Logger logger = LoggerFactory.getLogger(CustomerSupportAssistant.class);

    private final ChatClient.Builder chatClientBuilder;
    private final ChatMemory chatMemory;
    private final PatrolOrderTools patrolOrderTools;
    private final ChatService chatService;
    private final SystemPromptService systemPromptService;

    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    public CustomerSupportAssistant(ChatClient.Builder modelBuilder,
                                    VectorStore vectorStore,
                                    ChatMemory chatMemory,
                                    ChatService chatService,
                                    PatrolOrderTools patrolOrderTools,
                                    SystemPromptService systemPromptService) {
        this.chatClientBuilder = modelBuilder;
        this.chatService = chatService;
        this.patrolOrderTools = patrolOrderTools;
		this.chatMemory = chatMemory;
        this.systemPromptService = systemPromptService;
    }
    /**
     * 获取ChatClient（每次调用时使用最新的系统提示词）
     */
    private ChatClient getChatClient() {
        String systemPrompt = systemPromptService.getSystemPrompt();
        return chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                ).build();
    }


    public Flux<ServerSentEvent<String>> chatWithMetadata(ChatMessageRequest request) {
        String user = Optional.ofNullable(SecurityUtils.getUser()).map(UAPUser::getUsername).orElse("unknown");
        Integer chatType = request.getChatType();
        String userMessageContent = request.getQuery();
        String token = TokenContext.getToken();
        // 生成请求id
        String requestId = IdUtil.fastSimpleUUID();
        // 1. 检查是否需要创建新对话
        if (StringUtils.isEmpty(request.getChatId())) {
            request.setChatId(chatService.createChat(user, userMessageContent));
        }
        //  检查conservationId是否存在
        if (StringUtils.isEmpty(request.getConversationId())) {
            request.setConversationId(UUID.randomUUID().toString());
        }

        // 构建工具上下文(包含Token)
        Map<String, Object> toolContext = MapUtil.<String, Object>builder()
                .put("requestId", requestId)
                .put(TokenContext.TOKEN_KEY, token)
                .build();

        // 保存用户消息
        chatService.saveMessage(ChatDetail.builder()
                        .chatId(request.getChatId())
                        .chatType(chatType)
                        .role(MessageRole.USER.name())
                        .conversationId(request.getConversationId())
                        .content(userMessageContent)
                        .build());
        // 使用最新的系统提示词构建ChatClient
        ChatClient chatClient = getChatClient();
        Flux<String> content = chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .tools(patrolOrderTools)
                .advisors(
                        advisor -> advisor.param(CONVERSATION_ID, request.getConversationId()).param(TOP_K, 100))
                .toolContext(toolContext)
                .options(ToolCallingChatOptions.builder().build())
                .stream()
                .content();

        StringBuilder fullResponse = new StringBuilder();
        // 将内容流转换为 SSE 事件
        Flux<ServerSentEvent<String>> contentEvents = content
                .doFirst(() -> {
                    GENERATE_STATUS.put(request.getConversationId(), true);
                })
                .takeWhile(chunk -> GENERATE_STATUS.getOrDefault(request.getConversationId(), false))
                .doOnNext(chunk -> fullResponse.append(chunk))
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")  // 事件类型：普通消息
                        .data(chunk)
                        .build())
                .doOnComplete(() -> {
                    logger.info("Chat stream complete");
                    GENERATE_STATUS.remove(request.getConversationId());
                    if (fullResponse.length() > 0) {
                        chatService.saveMessage(
                                ChatDetail.builder()
                                        .chatId(request.getChatId())
                                        .chatType(chatType)
                                        .role(MessageRole.ASSISTANT.name())
                                        .conversationId(request.getConversationId())
                                        .content(fullResponse.toString())
                                        .build());
                    }
                })
                .doOnError(throwable -> {
                    logger.error("Chat stream error", throwable);
                    GENERATE_STATUS.remove(request.getConversationId());
                })
                .doOnCancel(() -> {
                    logger.info("Chat stream cancelled");
                    GENERATE_STATUS.remove(request.getConversationId());
                    if (fullResponse.length() > 0) {
                        chatService.saveMessage(
                                ChatDetail.builder()
                                        .chatId(request.getChatId())
                                        .chatType(chatType)
                                        .role(MessageRole.ASSISTANT.name())
                                        .conversationId(request.getConversationId())
                                        .content(fullResponse + " [已停止]")
                                        .build());
                    }
                });

        // 添加元数据事件
        ServerSentEvent<String> metadataEvent = ServerSentEvent.<String>builder()
                .event("metadata")  // 事件类型：元数据
                .data(buildMetadataJson(request.getConversationId(), request.getChatId(), userMessageContent))
                .build();

        ServerSentEvent<String> completeEvent = ServerSentEvent.<String>builder()
                .event("complete")  // 事件类型：完成
                .data("done")
                .build();
        return contentEvents
                .concatWith(Flux.defer(() -> {
                    Map<String, Object> map = ToolResultHolder.get(requestId);
                            if (CollUtil.isNotEmpty(map)) {
                                ToolResultHolder.remove(requestId); // 清除参数列表
                                //添加订单事件
                                ServerSentEvent<String> orderinfoEvent = ServerSentEvent.<String>builder()
                                        .event("orderinfo")  // 事件类型：订单数据
                                        .data(JSON.toJSONString(map))
                                        .build();
                                return Flux.just(metadataEvent, orderinfoEvent,completeEvent);
                            }
                             return Flux.just(metadataEvent,completeEvent);
                    }))
                .onBackpressureBuffer();
    }

    // 构建元数据 JSON
    private String buildMetadataJson(String conversationId, String chatId, String title) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("conversationId", conversationId);
        metadata.put("chatId", chatId);
        metadata.put("status", "completed");
        metadata.put("title", title);

        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Failed to serialize metadata\"}";
        }
    }

    /**
     * 停止指定会话的流式输出
     * @param chatId 会话ID
     * @return 是否成功停止
     */
    public boolean stopChat(String chatId) {
        if (GENERATE_STATUS.containsKey(chatId)) {
            GENERATE_STATUS.remove(chatId);
            logger.info("Stopped chat stream for chatId: {}", chatId);
            return true;
        }
        return false;
    }

    public Flux<String> chat(String chatId, String userMessageContent) {

        // 生成请求id
        String requestId = IdUtil.fastSimpleUUID();
        String token = "cbd4dee6-33b3-4833-ba7b-884f4d22a29b";
        TokenContext.setToken(token);
        // 构建工具上下文(包含Token)
        Map<String, Object> toolContext = MapUtil.<String, Object>builder()
                .put("requestId", requestId)
                .put(TokenContext.TOKEN_KEY, token)
                .build();

        // 使用最新的系统提示词构建ChatClient
        ChatClient chatClient = getChatClient();
        Flux<String> content = chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .tools(patrolOrderTools)
                .advisors(advisor -> advisor.param(CONVERSATION_ID, chatId).param(TOP_K, 100))
                .toolContext(toolContext)
                .options(ToolCallingChatOptions.builder().build())
                .stream()
                .content();
        // 收集完整响应并保存
        StringBuilder fullResponse = new StringBuilder();
        return content
                .doFirst(() -> {
                    // 流开始时标记为正在生成
                    GENERATE_STATUS.put(chatId, true);
                    logger.info("Started chat stream for chatId: {}", chatId);
                })
                .takeWhile(chunk -> {
                    // 检查是否应该继续输出
                    return GENERATE_STATUS.getOrDefault(chatId, false);
                })
                .doOnNext(chunk -> {
                    logger.info("Model output: {}", chunk);
                    if (!chunk.equals("[完成]")) {
                        fullResponse.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    logger.info("Chat stream complete for chatId: {}", chatId);
                    GENERATE_STATUS.remove(chatId);
                })
                .doOnError(throwable -> {
                    logger.error("Chat stream error for chatId: {}", chatId, throwable);
                    GENERATE_STATUS.remove(chatId);
                })
                .doOnCancel(() -> {
                    logger.info("Chat stream cancelled for chatId: {}", chatId);
                    GENERATE_STATUS.remove(chatId);
                })
                .concatWith(Flux.just("[完成]"))
                .onBackpressureBuffer(); // 添加背压缓冲 ;
    }
}
