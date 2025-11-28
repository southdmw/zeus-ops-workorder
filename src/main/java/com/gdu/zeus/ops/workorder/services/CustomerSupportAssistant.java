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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdu.zeus.ops.workorder.data.enums.MessageRole;
import com.gdu.zeus.ops.workorder.dto.ChatMessageRequest;
import com.gdu.zeus.ops.workorder.filter.TokenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import cn.hutool.core.map.MapUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
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

    private final ChatClient chatClient;
//    private final ChatSessionService sessionService; // 新增
    private final POIServiceV2 poiService;
    private final ChatService chatService;
    private final RouteServiceV2 routeService;
    private final PatrolOrderService patrolOrderService;

    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    public CustomerSupportAssistant(ChatClient.Builder modelBuilder,
                                    VectorStore vectorStore,
                                    ChatMemory chatMemory,
                                    ChatService chatService,
                                    POIServiceV2 poiService,
                                    RouteServiceV2 routeService,
                                    PatrolOrderService patrolOrderService,
                                    @Value("classpath:system-prompt.txt") Resource systemPromptResource) {
        this.chatService = chatService;
        this.poiService = poiService;
        this.routeService = routeService;
        this.patrolOrderService = patrolOrderService;
        // 从配置文件加载系统提示词
		String systemPrompt = loadSystemPrompt(systemPromptResource);
		// @formatter:off
		this.chatClient = modelBuilder
				.defaultSystem(systemPrompt)
				// 插件组合
				.defaultAdvisors(
						PromptChatMemoryAdvisor.builder(chatMemory).build(), // Chat Memory
						// new VectorStoreChatMemoryAdvisor(vectorStore)),
						new QuestionAnswerAdvisor(vectorStore), // RAG
						// new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()
						// 	.withFilterExpression("'documentType' == 'terms-of-service' && region in ['EU', 'US']")),
						// logger
						new SimpleLoggerAdvisor()
				).build();
		// @formatter:on
    }

	/**
	 * 从配置文件加载系统提示词
	 */
	private String loadSystemPrompt(Resource resource) {
		try {
            String systemPrompt = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            // 打印系统提示词到日志
            logger.info("=== 系统提示词加载成功 ===");
            logger.info("系统提示词内容:\n{}", systemPrompt);
            logger.info("=== 系统提示词加载完成 ===");
            return systemPrompt;
		} catch (IOException e) {
			logger.error("Failed to load system prompt from resource", e);
			throw new RuntimeException("Failed to load system prompt", e);
		}
	}

	//重构，加入userId
    public Flux<String> chat(ChatMessageRequest request,Object... additionalTools) {
        Integer chatType = request.getChatType();
        String userMessageContent = request.getQuery();
        String token = TokenContext.getToken();
        // 创建带token的Tool实例
        PatrolOrderTools toolsWithToken = new PatrolOrderTools(
                poiService,
                routeService,
                patrolOrderService,
                token  // 传递token
        );
        // 1. 检查是否需要创建新对话
        if (StringUtils.isEmpty(request.getChatId())) {
            request.setChatId(chatService.createChat("zbw3", userMessageContent));
        }
        //  检查conservationId是否存在
        if (StringUtils.isEmpty(request.getConversationId())) {
            request.setConversationId(UUID.randomUUID().toString());
        }

        // 保存用户消息
        chatService.saveMessage(request.getChatId(), chatType,MessageRole.USER, request.getConversationId(),userMessageContent);
        Flux<String> content = this.chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .tools(toolsWithToken)
                .advisors(
                        advisor -> advisor.param(CONVERSATION_ID, request.getConversationId()).param(TOP_K, 100))
                .toolContext(MapUtil.<String, Object>builder().put("token", token).build())
                .options(ToolCallingChatOptions.builder().build())
                .stream()
                .content();
        // 收集完整响应并保存
        StringBuilder fullResponse = new StringBuilder();
        return content
                .doFirst(() -> {
                    // 流开始时标记为正在生成
                    GENERATE_STATUS.put(request.getConversationId(), true);
                    logger.info("Started chat stream for conversationId: {}", request.getConversationId());
                })
                .takeWhile(chunk -> {
                    // 检查是否应该继续输出
                    return GENERATE_STATUS.getOrDefault(request.getConversationId(), false);
                })
                .doOnNext(chunk -> {
                    logger.info("Model output: {}", chunk);
                    if (!chunk.equals("[完成]")) {
                        fullResponse.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    logger.info("Chat stream complete for conversationId: {}", request.getConversationId());
                    GENERATE_STATUS.remove(request.getConversationId());
                    // 保存AI响应
                    if (fullResponse.length() > 0) {
                        chatService.saveMessage(request.getChatId(), chatType, MessageRole.ASSISTANT, request.getConversationId(), fullResponse.toString());
                    }
                })
                .doOnError(throwable -> {
                    logger.error("Chat stream error for conversationId: {}", request.getConversationId(), throwable);
                    GENERATE_STATUS.remove(request.getConversationId());
                })
                .doOnCancel(() -> {
                    logger.info("Chat stream cancelled for conversationId: {}", request.getConversationId());
                    GENERATE_STATUS.remove(request.getConversationId());
                    // 保存部分响应
                    if (fullResponse.length() > 0) {
                        chatService.saveMessage(request.getChatId(), chatType, MessageRole.ASSISTANT, request.getConversationId(),
                                fullResponse.toString() + " [已停止]");
                    }
                }).concatWith(Flux.just("[完成]"))
                .onBackpressureBuffer(); // 添加背压缓冲 ;
    }


    public Flux<ServerSentEvent<String>> chatWithMetadata(ChatMessageRequest request) {
        Integer chatType = request.getChatType();
        String userMessageContent = request.getQuery();
        String token = TokenContext.getToken();
        // 创建带token的Tool实例
        PatrolOrderTools toolsWithToken = new PatrolOrderTools(
                poiService,
                routeService,
                patrolOrderService,
                token  // 传递token
        );
        // 1. 检查是否需要创建新对话
        if (StringUtils.isEmpty(request.getChatId())) {
            request.setChatId(chatService.createChat("zbw3", userMessageContent));
        }
        //  检查conservationId是否存在
        if (StringUtils.isEmpty(request.getConversationId())) {
            request.setConversationId(UUID.randomUUID().toString());
        }

        // 保存用户消息
        chatService.saveMessage(request.getChatId(), chatType,MessageRole.USER, request.getConversationId(),userMessageContent);
        Flux<String> content = this.chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .tools(toolsWithToken)
                .advisors(
                        advisor -> advisor.param(CONVERSATION_ID, request.getConversationId()).param(TOP_K, 100))
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
                        chatService.saveMessage(request.getChatId(), chatType, MessageRole.ASSISTANT,
                                request.getConversationId(), fullResponse.toString());
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
                        chatService.saveMessage(request.getChatId(), chatType, MessageRole.ASSISTANT,
                                request.getConversationId(),
                                fullResponse.toString() + " [已停止]");
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
                .concatWith(Flux.just(metadataEvent, completeEvent))
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

    /*public Flux<String> chat(String chatId, String userMessageContent, Object... additionalTools) {
        Flux<String> content = this.chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .tools(additionalTools)
                .advisors(
                        // 设置advisor参数，记忆使用chatId，拉取最近的100条记录
                        advisor -> advisor.param(CONVERSATION_ID, chatId).param(TOP_K, 100))
                .options(ToolCallingChatOptions.builder().build())
                .stream()
                .chatResponse()
                .doFirst(() -> { //输出开始，标记正在输出
                    GENERATE_STATUS.put(chatId, true);
                })
                .doOnComplete(() -> { //输出结束，清除标记
                    GENERATE_STATUS.remove(chatId);
                    logger.info("Chat stream complete");
                })
                .doOnError(throwable -> GENERATE_STATUS.remove(chatId)) // 错误时清除标记
                //是否进行输出的条件，true：继续输出，false：停止输出
                .takeWhile(s -> GENERATE_STATUS.getOrDefault(chatId, false))
                .map(chatResponse -> {
                    // 获取大模型的输出的内容
                    String text = chatResponse.getResult().getOutput().getText();
                    return text;
                });
        return content
				.filter(text -> text != null && !text.isBlank())
                .doOnNext(resp -> logger.info("Model output: {}", resp))
				.concatWith(Flux.just("[complete]"));
    }*/


    public Flux<String> chat(String chatId, String userMessageContent) {
        String token = TokenContext.getToken();
        // 创建带token的Tool实例
        PatrolOrderTools toolsWithToken = new PatrolOrderTools(
                poiService,
                routeService,
                patrolOrderService,
                token  // 传递token
        );
        Flux<String> content = this.chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .tools(toolsWithToken)
                .advisors(advisor -> advisor.param(CONVERSATION_ID, chatId).param(TOP_K, 100))
                .options(ToolCallingChatOptions.builder().build())
//                .options(new TokenAwareChatOptions(token))
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
