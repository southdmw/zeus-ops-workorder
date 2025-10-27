package com.gdu.zeus.ops.workorder.services;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class POIService {

    public List<String> getLocationsByArea(String area) {
        if (area.contains("普宙科技") || area.contains("普宙")) {
            return Arrays.asList("黄龙山普宙科技", "未来一路普宙科技");
        }
        if (area.contains("光谷广场")) {
            return Arrays.asList("光谷广场东", "光谷广场西");
        }
        return Arrays.asList("默认位置A", "默认位置B");
    }
}
