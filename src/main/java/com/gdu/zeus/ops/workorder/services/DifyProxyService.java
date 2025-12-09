package com.gdu.zeus.ops.workorder.services;

import com.gdu.uap.auth.core.oauth2.UAPUser;
import com.gdu.uap.auth.core.util.SecurityUtils;
import com.gdu.zeus.ops.workorder.data.enums.MessageRole;
import com.gdu.zeus.ops.workorder.dto.ChatMessageRequest;
import com.gdu.zeus.ops.workorder.dto.LLmRecheckInputs;
import com.gdu.zeus.ops.workorder.dto.WorkFlowRequest;
import com.gdu.zeus.ops.workorder.dto.WorkflowResponse;
import com.gdu.zeus.ops.workorder.entity.ChatDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dify代理服务
 * 
 * 负责与Dify API进行交互，包括流式对话、文件上传、会话管理等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyProxyService {

    private final WebClient difyWebClient;
    private final ChatService chatService;

    private static final String CHAT_MESSAGES_PATH = "/v1/chat-messages";
    private static final String FILE_UPLOAD_PATH = "/v1/files/upload";
    private static final String STOP_PATH = "/v1/chat-messages/{taskId}/stop";
    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String RUN_WORKFLOW_PATH = "/v1/workflows/run";
    private static final String CONVERSATIONS_PATH = "/v1/conversations";
    private static final String DELETE_CONVERSATION_PATH = "/v1/conversations/{conversationId}";

    /**
     * 运行工作流(阻塞式)
     */
    public Mono<WorkflowResponse> runWorkflow(ChatMessageRequest request) {
        // 保存消息
        String user = Optional.ofNullable(SecurityUtils.getUser())
                .map(UAPUser::getUsername)
                .orElse("unknown");
        Integer chatType = request.getChatType();
        String title = extractWorkflowTitle(request);

        // 1. 检查是否需要创建新对话
        if (!StringUtils.hasText(request.getChatId())) {
            request.setChatId(chatService.createChat(user, title));
        }

        // 2. 检查conversationId是否存在
        if (!StringUtils.hasText(request.getConversationId())) {
            request.setConversationId(UUID.randomUUID().toString());
        }

        ChatDetail chatDetail = ChatDetail.builder()
                .chatId(request.getChatId())
                .chatType(chatType)
                .role(MessageRole.USER.name())
                .conversationId(request.getConversationId())
                .content(title)
                .imgUrl(request.getInputs().getImage().getUrl())
                .conversationStopFlag(1)
                .build();
        // 3. 保存用户消息
        chatService.saveMessage(chatDetail);

        // 4. 构造Dify工作流请求体
        WorkFlowRequest workFlowRequest = WorkFlowRequest.builder()
                .inputs(request.getInputs())  // 设置工作流输入参数
                .responseMode("blocking")
                .user(user)
                .conversationId(request.getConversationId())
                .autoGenerateName(true)
                .traceId(UUID.randomUUID().toString())
                .build();

        log.info("运行工作流 - inputs: {}, traceId: {}",
                workFlowRequest.getInputs(), workFlowRequest.getTraceId());

        // 5. 调用Dify API
        return difyWebClient.post()
                .uri(RUN_WORKFLOW_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(workFlowRequest)  // 使用构造好的workFlowRequest
                .retrieve()
                .bodyToMono(WorkflowResponse.class)
                .doOnSuccess(response -> {
                    response.setChatId(request.getChatId());
                    response.setTitle(title);
                    log.info("运行工作流成功 - taskId: {}, status: {}",
                            response.getTaskId(),
                            response.getData().getStatus());

                    // 6. 提取识别结果
                    String taskResult = extractWorkflowOutput(response);

                    // 7. 保存AI助手消息
                    chatService.saveMessage(
                            ChatDetail.builder()
                                    .chatId(request.getChatId())
                                    .chatType(chatType)
                                    .role(MessageRole.ASSISTANT.name())
                                    .conversationId(request.getConversationId())
                                    .content(taskResult)
                                    .imgUrl(request.getInputs().getImage().getUrl())
                                    .conversationStopFlag(1)
                                    .build()
                    );
                })
                .doOnError(error -> log.error("运行工作流失败", error));
    }

    /**
     * 从工作流响应中提取输出结果
     */
    private String extractWorkflowOutput(WorkflowResponse response) {
        try {
            Map<String, Object> outputs = response.getData().getOutputs();
            if (outputs != null && outputs.containsKey("text")) {
                Map<String, Object> textOutput = (Map<String, Object>) outputs.get("text");
                if (textOutput != null && textOutput.containsKey("output")) {
                    String output = String.valueOf(textOutput.get("output"));
                    return "识别结果：" + output;
                }
            }
            return "识别结果：无输出";
        } catch (Exception e) {
            log.error("提取工作流输出失败", e);
            return "识别结果：解析失败";
        }
    }

    /**
     * 从请求中提取title
     */
    private String extractWorkflowTitle(ChatMessageRequest request) {
        try {
            LLmRecheckInputs inputs = request.getInputs();
            if (inputs != null && inputs.getWarningType() != null) {
                return "请问图片是否包含如下告警目标：" + inputs.getWarningType();
            }
        }
        catch (Exception e) {
            log.error("提取title失败", e);
            return "请问图片是否包含如下告警目标";
        }
        return "请问图片是否包含如下告警目标";
    }

    /**
     * 运行工作流（内部调用阻塞式）
     */
    public Mono<WorkflowResponse> runWorkflowInner(WorkFlowRequest request) {
        log.info("运行工作流 - inputs: {}", request.getInputs());

        return difyWebClient.post()
                .uri(RUN_WORKFLOW_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(WorkflowResponse.class)
                .doOnSuccess(response -> log.info("运行工作流成功 - taskId: {}", response.getTaskId()))
                .doOnError(error -> log.error("运行工作流失败", error));
    }

    // 内部类 - 请求体
    private record StopRequest(String user) {}
    private record DeleteRequest(String user) {}
}