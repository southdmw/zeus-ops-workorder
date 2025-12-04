package com.gdu.zeus.ops.workorder.services;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class AIAlgorithmService  {
    public List<String> searchAlgorithms(String keyword) {
        // 基于关键词进行语义搜索
        if (keyword.contains("停车") || keyword.contains("占道")) {
            return Arrays.asList("机动车占道停放检测", "违章停车识别算法");
        }
        return Arrays.asList("通用目标检测", "行为分析算法");
    }
}
