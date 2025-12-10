package com.gdu.zeus.ops.workorder.services;

import com.gdu.zeus.ops.workorder.client.dto.WorkOrderApiDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统提示词服务
 * 负责加载和动态生成系统提示词
 */
@Slf4j
//@Service
public class SystemPromptService {

    private final WorkOrderExternalService workOrderExternalService;
    private final String externalPromptPath;
    private final Resource classpathPromptResource;

    // 缓存生成的提示词
    private volatile String cachedSystemPrompt;
    private volatile long lastUpdateTime = 0;
    private static final long CACHE_DURATION = 3600000; // 1小时缓存

    public SystemPromptService(
            WorkOrderExternalService workOrderExternalService,
            @Value("${system-prompt.file-path:}") String externalPromptPath,
            @Value("classpath:system-prompt.txt") Resource classpathPromptResource) {
        this.workOrderExternalService = workOrderExternalService;
        this.externalPromptPath = externalPromptPath;
        this.classpathPromptResource = classpathPromptResource;
    }

    /**
     * 获取系统提示词（带缓存）
     */
    public String getSystemPrompt() {
        long currentTime = System.currentTimeMillis();
        // 如果缓存有效，直接返回
        if (cachedSystemPrompt != null && (currentTime - lastUpdateTime) < CACHE_DURATION) {
            log.info("使用缓存的系统提示词");
            return cachedSystemPrompt;
        }
        // 重新生成提示词
        synchronized (this) {
            // 双重检查
            if (cachedSystemPrompt != null && (currentTime - lastUpdateTime) < CACHE_DURATION) {
                return cachedSystemPrompt;
            }
            log.info("=== 开始生成系统提示词 ===");
            cachedSystemPrompt = generateSystemPrompt();
            lastUpdateTime = currentTime;
            log.info("=== 系统提示词生成完成 ===");

            return cachedSystemPrompt;
        }
    }

    /**
     * 强制刷新系统提示词
     */
    /*public String refreshSystemPrompt() {
        log.info("强制刷新系统提示词");
        synchronized (this) {
            cachedSystemPrompt = generateSystemPrompt();
            lastUpdateTime = System.currentTimeMillis();
            return cachedSystemPrompt;
        }
    }*/
    /**
     * 生成系统提示词
     */
    private String generateSystemPrompt() {
        try {
            // 1. 加载提示词模板
            String template = loadPromptTemplate();
            // 2. 获取动态数据
            Map<String, String> placeholders = fetchDynamicData();
            // 3. 替换占位符
            String systemPrompt = replacePlaceholders(template, placeholders);

            log.info("系统提示词长度: {} 字符", systemPrompt.length());
            log.info("系统提示词内容:\n{}", systemPrompt);

            return systemPrompt;

        } catch (Exception e) {
            log.error("生成系统提示词失败", e);
            // 返回默认模板（不包含动态数据）
            return loadPromptTemplateWithoutDynamicData();
        }
    }

    /**
     * 加载提示词模板
     */
    private String loadPromptTemplate() {
        try {
            // 优先使用外部文件
            if (externalPromptPath != null && !externalPromptPath.trim().isEmpty()) {
                Path path = Paths.get(externalPromptPath);
                if (Files.exists(path)) {
                    log.info("从外部文件加载提示词模板: {}", externalPromptPath);
                    return Files.readString(path, StandardCharsets.UTF_8);
                } else {
                    log.warn("外部文件不存在: {}, 降级使用classpath资源", externalPromptPath);
                }
            }

            // 使用classpath资源
            log.info("从classpath加载提示词模板");
            return StreamUtils.copyToString(
                    classpathPromptResource.getInputStream(),
                    StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new RuntimeException("加载提示词模板失败", e);
        }
    }

    /**
     * 获取动态数据
     */
    private Map<String, String> fetchDynamicData() {
        Map<String, String> placeholders = new HashMap<>();
        try {
            // 1. 获取工单性质列表
            List<WorkOrderApiDto.OrderNatureResponse> natures =
                    workOrderExternalService.getNatureList();

            if (natures != null && !natures.isEmpty()) {
                String orderNatures = natures.stream()
                        .map(WorkOrderApiDto.OrderNatureResponse::getDescription)
                        .collect(Collectors.joining("、"));
                placeholders.put("ORDER_NATURES", orderNatures);
                log.info("成功获取工单性质列表: {}", orderNatures);
            } else {
                // 使用默认值
                String defaultNatures = "野外建设巡查、空中巡查、护林防御、探测大气、摄影、空中摄影、测绘、其他";
                placeholders.put("ORDER_NATURES", defaultNatures);
                log.warn("未获取到工单性质列表，使用默认值");
            }
            // 2. 可以在这里添加其他动态数据的获取
            // 例如：巡查结果类型、执行方式等

        } catch (Exception e) {
            log.error("获取动态数据失败，使用默认值", e);
            // 使用默认值
            String defaultNatures = "野外建设巡查、空中巡查、护林防御、探测大气、摄影、空中摄影、测绘、其他";
            placeholders.put("ORDER_NATURES", defaultNatures);
        }

        return placeholders;
    }

    /**
     * 替换占位符
     */
    private String replacePlaceholders(String template, Map<String, String> placeholders) {
        String result = template;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue();
            result = result.replace(placeholder, value);
        }

        return result;
    }

    /**
     * 加载不包含动态数据的模板（降级方案）
     */
    private String loadPromptTemplateWithoutDynamicData() {
        try {
            String template = loadPromptTemplate();
            // 使用默认值替换所有占位符
            Map<String, String> defaults = new HashMap<>();
            defaults.put("ORDER_NATURES", "野外建设巡查、空中巡查、护林防御、探测大气、摄影、空中摄影、测绘、其他");
            return replacePlaceholders(template, defaults);
        } catch (Exception e) {
            log.error("加载降级模板失败", e);
            // 返回最基本的提示词
            return "你是无人机运维服务平台的智能助手，请协助用户创建巡查工单。";
        }
    }
}
