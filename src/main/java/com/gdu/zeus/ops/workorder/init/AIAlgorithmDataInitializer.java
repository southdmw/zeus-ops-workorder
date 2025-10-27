package com.gdu.zeus.ops.workorder.init;

import com.gdu.zeus.ops.workorder.repository.AIAlgorithmRepository;
import com.gdu.zeus.ops.workorder.services.PatrolOrderTools;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AIAlgorithmDataInitializer {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AIAlgorithmRepository algorithmRepository;

    private static final Logger logger = LoggerFactory.getLogger(PatrolOrderTools.class);

    @PostConstruct
    public void initAlgorithmData() {
        if (algorithmRepository.count() != 0) {
            // 初始化向量存储
            initVectorStore();
        }
    }

/*    private void initVectorStore() {
        List<Document> algorithmDocs = algorithmRepository.findAll().stream()
                .map(algo -> new Document(algo.getAlgorithmFunction(),
                        Map.of("algorithmName", algo.getAlgorithmName(),
                                "scenario", algo.getScenario(),"algorithmUsage",algo.getAlgorithmUsage())
                ))
                .collect(Collectors.toList());

        vectorStore.write(algorithmDocs);
    }*/

    private void initVectorStore() {
        logger.info("初始化向量数据库开始--------");
        List<Document> algorithmDocs = algorithmRepository.findAll().stream()
                .map(algo -> {
                    // 构建更丰富的文本内容供向量化处理
                    /*String content = String.join("\n",
                            "算法名称：" + algo.getAlgorithmName(),
                            "功能描述：" + algo.getAlgorithmFunction(),
                            "使用方式：" + algo.getAlgorithmUsage(),
                            "应用场景：" + algo.getScenario());*/
                    String content = String.format(
                            "这是一个名为%s的人工智能算法。它的主要功能是：%s。该算法通常用于以下场景：%s。使用方法包括：%s。",
                            algo.getAlgorithmName(),
                            algo.getAlgorithmFunction(),
                            algo.getScenario(),
                            algo.getAlgorithmUsage()
                    );
                    // 元信息仍然保留原样
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("algorithmName", algo.getAlgorithmName());
                    metadata.put("algorithmFunction",algo.getAlgorithmFunction());
                    metadata.put("algorithmUsage", algo.getAlgorithmUsage());
                    metadata.put("scenario", algo.getScenario());
                    return new Document(content, metadata);
                })
                .collect(Collectors.toList());
        vectorStore.write(algorithmDocs);
    }
}
