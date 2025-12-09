package com.gdu.zeus.ops.workorder.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowResponse {
    private String taskId;
    private String chatId;
    private String title;
    private String workflowRunId;
    private WorkflowData data;
    
    @Data
    public static class WorkflowData {
        private String id;
        private String workflowId;
        private String status;
        private Map<String, Object> outputs;
        private String error;
        private Double elapsedTime;
        private Integer totalTokens;
        private Integer totalSteps;
        private Long createdAt;
        private Long finishedAt;
    }
}