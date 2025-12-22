package com.gdu.zeus.ops.workorder.client;

import com.gdu.common.core.util.R;
import com.gdu.uap.auth.client.security.annotation.Inner;
import com.gdu.zeus.ops.workorder.config.DifyClientConfig;
import com.gdu.zeus.ops.workorder.dto.ChatMessageRequest;
import com.gdu.zeus.ops.workorder.dto.WorkFlowRequest;
import com.gdu.zeus.ops.workorder.dto.WorkflowResponse;
import com.gdu.zeus.ops.workorder.services.DifyProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 对话页面
 */
@Slf4j
@RestController
@RequestMapping("/api/dify")
@RequiredArgsConstructor
public class DifyController {

    private final DifyProxyService difyProxyService;
    private final DifyClientConfig.DifyProperties difyProperties;

    /**
     * 运行dify工作流
     *
     * 等待完整响应后返回，适用于简单场景
     *
     * @param request 聊天请求
     * @return 响应结果
     */
    @Inner
    @PostMapping("/inner/run-workflow")
    public Mono<ResponseEntity<WorkflowResponse>> runWorkflowInner(
            @RequestBody WorkFlowRequest request) {
        request.setUser("zeus-ops-ai");
        String userId = request.getUser();
        String traceId = request.getTraceId();
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
            request.setTraceId(traceId);
        }

        log.info("收到阻塞聊天请求 - userId: {}, traceId: {}",
                userId, traceId);

        return difyProxyService.runWorkflowInner(request)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("阻塞聊天失败", error);
                    // 返回错误信息
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(null));
                });
    }

    /**
     * 大模型复检
     * @param request
     * @return
     */
    @PostMapping("/run-workflow")
    public Mono<ResponseEntity<WorkflowResponse>> runWorkflow(
            @RequestBody ChatMessageRequest request) {

        return difyProxyService.runWorkflow(request)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("阻塞聊天失败", error);
                    // 返回错误信息
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(null));
                });
    }

    /**
     * 获取大模型复检支持算法
     */
    @Inner
    @GetMapping("/inner/get-support-llmAlarmTypes")
    public R getSupportAlgorithmsInner() {
        return R.ok(difyProperties.getAlarmTypes());
    }


    /**
     * 获取大模型复检支持算法
     */
    @GetMapping("/get-support-llmAlarmTypes")
    public R getSupportAlgorithms() {
        Map<String, String> alarmTypes = difyProperties.getAlarmTypes();
//        Map<String, String> reversedMap = alarmTypes.entrySet().stream()
//                .collect(Collectors.toMap(
//                        Map.Entry::getValue,           // value 作为新的 key
//                        Map.Entry::getKey,             // key 作为新的 value
//                        (existing, replacement) -> existing,  // 遇到重复时保留第一个
//                        LinkedHashMap::new             // 保持插入顺序（可选）
//                ));

        Map<String, String> reversedMap = new HashMap<>();
        for (Map.Entry<String, String> entry : alarmTypes.entrySet()) {
            reversedMap.putIfAbsent(entry.getValue(), entry.getKey());
        }
        return R.ok(reversedMap);
    }

}