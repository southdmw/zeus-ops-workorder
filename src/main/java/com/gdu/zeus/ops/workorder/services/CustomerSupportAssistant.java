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

import com.gdu.zeus.ops.workorder.data.enums.MessageRole;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
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
    private final ChatSessionService sessionService; // 新增
    private final POIServiceV2 poiService;
    private final RouteServiceV2 routeService;
    private final PatrolOrderService patrolOrderService;

    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    public CustomerSupportAssistant(ChatClient.Builder modelBuilder,
                                    VectorStore vectorStore,
                                    ChatMemory chatMemory,
                                    ChatSessionService sessionService,
                                    POIServiceV2 poiService,
                                    RouteServiceV2 routeService,
                                    PatrolOrderService patrolOrderService,
                                    @Value("classpath:system-prompt.txt") Resource systemPromptResource) {
        this.sessionService = sessionService;
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
    public Flux<String> chat(String userId,
                             String chatId,
                             String userMessageContent,
                             Object... additionalTools) {
        // 保存用户消息
        sessionService.saveMessage(userId, chatId, MessageRole.USER, userMessageContent);
        Flux<String> content = this.chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .tools(additionalTools)
                .advisors(
                        advisor -> advisor.param(CONVERSATION_ID, chatId).param(TOP_K, 100))
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
                    // 保存AI响应
                    if (fullResponse.length() > 0) {
                        sessionService.saveMessage(userId, chatId, MessageRole.ASSISTANT, fullResponse.toString());
                    }
                })
                .doOnError(throwable -> {
                    logger.error("Chat stream error for chatId: {}", chatId, throwable);
                    GENERATE_STATUS.remove(chatId);
                })
                .doOnCancel(() -> {
                    logger.info("Chat stream cancelled for chatId: {}", chatId);
                    GENERATE_STATUS.remove(chatId);
                    // 保存部分响应
                    if (fullResponse.length() > 0) {
                        sessionService.saveMessage(userId, chatId, MessageRole.ASSISTANT,
                                fullResponse.toString() + " [已停止]");
                    }
                }).concatWith(Flux.just("[完成]"))
                .onBackpressureBuffer(); // 添加背压缓冲 ;
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
        String token = "yands123456";
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
