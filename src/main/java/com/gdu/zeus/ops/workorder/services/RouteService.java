package com.gdu.zeus.ops.workorder.services;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class RouteService {

    public List<String> getRoutesByLocation(String location) {
        if (location.contains("光谷广场")) {
            return Arrays.asList("光谷广场1巡查线", "光谷广场2环绕线");
        }
        if (location.contains("普宙科技")) {
            return Arrays.asList("普宙科技周边巡查线", "普宙科技环绕线");
        }
        return Arrays.asList("默认巡查航线A", "默认巡查航线B");
    }
}
