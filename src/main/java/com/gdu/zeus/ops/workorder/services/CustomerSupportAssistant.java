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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

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

	private final ChatClient chatClient;

	public CustomerSupportAssistant(ChatClient.Builder modelBuilder, PatrolOrderTools patrolOrderTools, VectorStore vectorStore, ChatMemory chatMemory) {

		// @formatter:off
		this.chatClient = modelBuilder
				.defaultSystem("""
				你是无人机运维服务平台的智能助手，专责引导用户创建巡查工单。请严格遵循以下规则：
    
				工单信息收集与确认
				在创建工单前，必须收集并确认以下字段：
				1、工单名称：由系统根据时间自动生成（例如：“光谷广场日常巡查-yyyyMMddHHmmss”），无需用户输入或确认。
				2、巡查区域：明确巡查地点（如“光谷广场”），由用户提供并确认。
				3、巡查目标：明确巡查目的（如“检测违章停车”），由用户提供并确认。
				4、执行时间：格式为“yyyy-MM-dd HH:mm”，由用户提供并确认。
				5、执行航线：
					用户提供巡查区域后，调用 getAvailableRoutes(巡查区域) 获取可用航线；
					推荐最匹配的航线给用户，由用户确认选择。
				6、工单描述：系统自动根据上下文生成并展示给用户参考，无需用户确认。
				7、巡查结果类型（照片 / 视频）：
					如果用户未提供，系统可默认一个合理值；
					由用户确认选择。
				8、执行方式（单次 / 多次 / 自定义）：
					如果用户未提供，系统可默认一个合理值；
					由用户确认选择。
				
				提示与交互规则
					每收集并确认一个字段后，不重复确认已确认项。
					若信息不全，主动引导用户补充，避免跳过或使用不经确认的默认值。
					所有关键选项（算法、航线、结果类型、执行方式）必须经过用户确认选择。
				
				工具函数使用规范
					提供巡查区域后，调用 getAvailableRoutes(巡查区域) 获取可用航线。
					调用 createPatrolOrder(...) 前，必须征得用户明确同意。
				
				操作限制
					未征得用户确认前，严禁自动调用 createPatrolOrder。
					所有操作需明确告知用户并获得用户同意后执行。
				
				其他
					当前日期为 {current_date}，应结合此日期提供上下文相关建议
				""")
				// 插件组合
				.defaultAdvisors(
						PromptChatMemoryAdvisor.builder(chatMemory).build(), // Chat Memory
						// new VectorStoreChatMemoryAdvisor(vectorStore)),
					
						new QuestionAnswerAdvisor(vectorStore), // RAG
						// new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()
						// 	.withFilterExpression("'documentType' == 'terms-of-service' && region in ['EU', 'US']")),

						// logger
						new SimpleLoggerAdvisor()
				)/*.defaultToolNames(
						"createBooking",
						"getBookingDetails",
						"changeBooking",
						"cancelBooking"
				)*/.defaultTools(patrolOrderTools).build();
		// @formatter:on
	}

	public Flux<String> chat(String chatId, String userMessageContent,Object... additionalTools) {
		Flux<String> content = this.chatClient.prompt()
				.system(s -> s.param("current_date", LocalDate.now().toString()))
				.user(userMessageContent)
				.tools(additionalTools)
				.advisors(
						// 设置advisor参数，
						// 记忆使用chatId，
						// 拉取最近的100条记录
						advisor  -> advisor .param(CONVERSATION_ID, chatId).param(TOP_K, 100))
				.stream()
				.content();
		return  content
				.doOnNext(System.out::println)
				.concatWith(Flux.just("[complete]"));
	}
}
