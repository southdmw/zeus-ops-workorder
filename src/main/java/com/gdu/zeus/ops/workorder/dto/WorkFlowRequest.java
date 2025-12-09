package com.gdu.zeus.ops.workorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkFlowRequest {



    /**
     * 应用定义的变量值
     */
    private LLmRecheckInputs inputs;

    /**
     * 响应模式: streaming(流式) 或 blocking(阻塞)
     */
    @JsonProperty("response_mode")
    @Builder.Default
    private String responseMode = "blocking";

    /**
     * 用户标识
     */
    private String user;

    /**
     * 会话ID (选填，继续对话时需要)
     */
    @JsonProperty("conversation_id")
    private String conversationId;



    /**
     * 是否自动生成标题
     */
    @JsonProperty("auto_generate_name")
    @Builder.Default
    private Boolean autoGenerateName = true;

    /**
     * 链路追踪ID
     */
    @JsonProperty("trace_id")
    private String traceId;

}