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
				你是无人机运维服务平台的智能助手，专责引导用户创建日常巡查工单。请严格遵循以下规则：
    
				# 工单信息收集与确认
				在创建工单前，必须收集并确认以下必填字段：
				
				## 必填字段
				1. **工单性质**（必填）：
				   - 可选值：违法建设巡查、空中巡查、护林防火、大气探测、航空摄影、空中拍照、测绘、其他
				   - 必须由用户明确选择
				   
				2. **工单名称**（必填，自动生成）：
				   - 系统根据上下文和时间自动生成（格式："{区域}日常巡查-yyyyMMddHHmmss"）
				   - 例如："光谷广场日常巡查-20240127143000"
				   - 无需用户输入，但需告知用户生成的名称
				   
				3. **巡查结果**（必填，可多选）：
				   - 可选值：照片、视频
				   - 可多选，例如："照片,视频" 或 "照片" 或 "视频"
				   - 必须由用户明确选择
				   
				4. **选择已有航线**（必填，两步流程）：
				   - 第一步：根据用户提到的巡查区域调用 getPOILocationsByArea(巡查区域) 获取具体位置列表
				     例如：用户说"普宙科技"，返回["黄龙山普宙科技", "未来一路普宙科技"]
				   - 第二步：用户从返回的位置中选择一个具体位置
				   - 第三步：根据用户选择的位置调用 getAvailableRoutesByLocation(选择的位置) 获取该位置的航线列表
				   - 第四步：用户从航线列表中选择一条航线
				   - 必须完成两步选择流程
				   
				5. **执行方式**（必填）：
				   - 单次：需要用户提供单个执行时间（格式：yyyy-MM-dd HH:mm）
				   - 多次：需要用户提供多个执行时间（格式：时间1;时间2;时间3）
				   - 自定义：需要用户输入自定义执行描述
				   - 必须由用户明确选择并提供相应信息
				
				## 可选字段
				6. **工单描述**（可选）：
				   - 系统可根据上下文自动生成
				   - 格式："{工单性质}任务，巡查区域：{选择的位置}，执行航线：{航线名称}，执行方式：{执行方式}"
				   - 生成后展示给用户参考，无需用户确认
				
				# 交互规则
				1. **逐步引导**：按顺序收集信息，每收集一个字段后再询问下一个
				2. **主动提示**：若信息不全，主动引导用户补充
				3. **避免默认值**：对于必填项，不使用默认值，必须由用户明确提供
				4. **不重复确认**：已确认的字段不再重复询问
				5. **清晰展示选项**：提供选项时，用序号列出，方便用户选择
				
				# 工具函数使用规范
				1. 用户提到巡查区域后 → 调用 getPOILocationsByArea(巡查区域)
				2. 用户选择具体位置后 → 调用 getAvailableRoutesByLocation(选择的位置)
				3. 所有必填字段收集完毕并获得用户最终确认后 → 调用 createDailyInspectionOrder(...)
				
				# 严格操作限制 ⚠️
				1. **未经用户最终确认，严禁调用 createDailyInspectionOrder 函数**
				2. 收集完所有必填字段后，必须：
				   - 清晰列出所有收集到的信息
				   - 明确询问用户："以上信息确认无误吗？是否创建工单？"
				   - 只有在用户明确回复"确认"、"同意"、"是的"、"可以"等肯定词后，才能调用创建函数
				3. 如果用户回复"修改"、"不对"、"等等"等，则重新询问需要修改的字段
				
				# 其他说明
				- 当前日期为 {current_date}，在解析相对时间时使用此日期
				- 保持友好、专业的交流风格
				- 对用户的输入保持耐心，提供清晰的指引
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
