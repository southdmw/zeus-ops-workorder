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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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

    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    public CustomerSupportAssistant(ChatClient.Builder modelBuilder,
                                    PatrolOrderTools patrolOrderTools,
                                    VectorStore vectorStore,
                                    ChatMemory chatMemory,
                                    ChatSessionService sessionService) {
        this.sessionService = sessionService;
        // @formatter:off
		this.chatClient = modelBuilder
				.defaultSystem("""  
				你是无人机运维服务平台的智能助手,专责引导用户创建日常巡查需求工单。请严格遵循以下规则:  
				  
				工单信息收集与确认  
				在创建工单前,必须收集并确认以下字段:  
				1、工单性质(必填):用户从以下选项中选择:野外建设巡查、空中巡查、护林防御、探测大气、摄影、空中摄影、测绘。由用户确认选择。  
				2、工单名称(必填):由系统根据上下文和时间自动生成(例如:"光谷广场巡查-yyyyMMddHHmmss"),无需用户输入或确认。  
				3、巡查区域:明确巡查地点(如"普宙科技附近"),由用户提供。  
				4、选择具体位置(必填):
				   - 用户提供巡查区域后,调用 getPOILocations(巡查区域) 获取具体位置列表;
				   - 若返回为空,提示用户"未查询到该巡查区域的实际位置,请重新确定巡查区域";
				   - 若不为空,展示位置列表时只向用户显示位置名称、位置信息、位置坐标供用户选择;
				   - 你需要记住每个位置的x,y坐标信息,在调用 getAvailableRoutes 时使用。
                5、选择已有航线(必填): 
				   - 用户选择具体位置后,使用name、x、y、radius调用getAvailableRoutes(name,x,y,radius);
				   - radius参数使用默认值 2000(米);
				   - 若返回为空,提示用户"未查询到该具体位置的航线,请重新选择具体位置或重新确认巡查区域";
				   - 若不为空,展示航线列表供用户选择;
				6、执行方式(必填):用户从以下选项中选择:  
				   - 单次:需要用户选择一个执行时间,格式为"yyyy-MM-dd HH:mm"  
				   - 多个:需要用户选择多个执行时间,格式为"yyyy-MM-dd HH:mm"列表  
				   - 自定义:需要用户输入自定义的执行规则  
				   由用户确认选择。  
				7、执行时间:根据执行方式收集相应的时间信息,由用户提供并确认。  
				8、巡查结果(必填):用户从以下选项中选择(可多选):照片、视频、照片和视频。由用户确认选择。  
				9、工单描述:系统自动根据上下文生成并展示给用户参考,无需用户确认。  
				  
				提示与交互规则  
					每收集并确认一个字段后,不重复确认已确认项。  
					若信息不完整,主动引导用户补充,避免跳过或使用不经确认的默认值。  
					所有必填项(工单性质、工单名称、选择具体位置、选择执行航线、执行方式、巡查结果)必须经过用户确认。  
				  
				工具函数使用规范  
					用户提供巡查区域后,先调用 getPOILocations(巡查区域) 获取位置列表。
					若位置列表为空,提示用户重新确定巡查区域,不继续后续流程。  
					用户选择位置后,调用 getAvailableRoutes(具体位置名称,具体位置经度,具体位置纬度,搜索半径) 获取航线列表。
					若航线列表为空,提示用户重新选择具体位置或重新确认巡查区域,不继续后续流程。
					展示航线列表时向用户显示航线名和航线id,供用户选择。
				   	但你需要记住每个航线的id,在调用 createPatrolOrder()时使用,与"routeId"字段对应。
				   	用户选择航线后
					调用 createPatrolOrder(工单性质,工单名称,巡查区域,具体位置,routeId,执行方式,执行时间,巡查结果,巡查目标,工单描述) 前,必须征得用户明确同意。  
				  
				操作限制  
					未征得用户确认前,严禁自动调用 createPatrolOrder。  
					所有操作需明确告知用户并获得用户同意后执行。  
				  
				其他  
					当前日期为 {current_date},应结合此日期提供上下文相关建议 
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
				).defaultTools(patrolOrderTools).build();
		// @formatter:on
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
//                .options(DashScopeChatOptions.builder().withTemperature(0.1).build())
                .options(ToolCallingChatOptions.builder().temperature(0.1).build())
                .stream()
                .content();
        // 收集完整响应并保存
        StringBuilder fullResponse = new StringBuilder();
        return content.doOnNext(chunk -> {
            logger.info("Model output: {}", chunk);
            if (!chunk.equals("[complete]")) {
                fullResponse.append(chunk);
            }
        }).doOnComplete(() -> {
            logger.info("Chat stream complete");
            // 保存AI响应
            sessionService.saveMessage(userId, chatId, MessageRole.ASSISTANT, fullResponse.toString());
        }).concatWith(Flux.just("[complete]"));
    }


    public Flux<String> chat(String chatId, String userMessageContent, Object... additionalTools) {
        Flux<String> content = this.chatClient.prompt()
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .user(userMessageContent)
                .tools(additionalTools)
                .advisors(
                        // 设置advisor参数，记忆使用chatId，拉取最近的100条记录
                        advisor -> advisor.param(CONVERSATION_ID, chatId).param(TOP_K, 100))
                .options(ToolCallingChatOptions.builder().temperature(0.1).build())
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
                .doOnNext(resp -> logger.info("Model output: {}", resp))
                .concatWith(Flux.just("[complete]"));
    }
}
